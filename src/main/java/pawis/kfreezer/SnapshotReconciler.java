package pawis.kfreezer;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.vertx.core.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pawis.kfreezer.model.KFSnapshot;
import pawis.kfreezer.model.KFSnapshotStatus;

import javax.inject.Inject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;


@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE)
public class SnapshotReconciler implements Reconciler<KFSnapshot>, Cleaner<KFSnapshot> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotReconciler.class);

    @Inject
    KubernetesClient client;
    @Inject
    SnapshotRepository snapshotRepo;

    @Override
    public UpdateControl<KFSnapshot> reconcile(KFSnapshot kfSnapshot,
                                               Context<KFSnapshot> context) throws IOException, InterruptedException {
        var status = kfSnapshot.getStatus();
        if (Objects.equals(status.getState(), KFSnapshotStatus.PENDING)) {
            doSnapshot(kfSnapshot);
            if (status.getError()!=null) {
                status.setState(KFSnapshotStatus.FAILED);
            }
            return UpdateControl.updateResourceAndStatus(kfSnapshot);
        }
        return UpdateControl.noUpdate();
    }

    private void doSnapshot(KFSnapshot kfSnapshot) throws InterruptedException {
        var status = kfSnapshot.getStatus();
        var spec = kfSnapshot.getSpec();
        var jobName = kfSnapshot.getSpec().getJob();
        var kfsName = kfSnapshot.getMetadata().getName();
        LOGGER.info("KFSnapshot '{}': start with job '{}'", kfsName, jobName);
        var job = client.batch().v1().jobs().withName(jobName).get();
        if (job==null) {
            status.setError("Cannot find job '%s'".formatted(spec.getJob()));
            return;
        }
        LOGGER.info("KFSnapshot '{}': found match job {}", kfsName, job.getMetadata().getUid());
        status.setJobSpecs(Json.encode(job.getSpec()));
        status.setSnapshotTime(Instant.now().toString());
        var pods = client.pods().withLabel("job-name", jobName)
                .list().getItems();
        if (pods.isEmpty()) {
            status.setError("Cannot find any pods from the job '%s'".formatted(spec.getJob()));
            return;
        }
        var pod = pods.get(0);
        var podName = pod.getMetadata().getName();
        spec.setPod(podName);
        status.setState(KFSnapshotStatus.COMPLETED);
        var containerName = pod.getSpec().getContainers().get(0).getName();
        LOGGER.info("KFSnapshot '{}': pod: '{}', container: '{}'",
                kfsName, podName, containerName);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<String> logFuture = new CompletableFuture<>();
        var url = snapshotRepo.allocate(kfSnapshot);
        LOGGER.info("KFSnapshot '{}': presigned url: '{}'",
                kfsName, url);
        var startTime = System.currentTimeMillis();
        var cmd = """
                mkdir -p .snapshot-image;
                CHECKPOINT_DIR="$HOME/.fastfreeze"
                fastfreeze checkpoint --leave-running;
                tar -czf snapshot-image.tar -C $HOME/.fastfreeze .;
                ls -al;
                CURL_CMD="curl -sS -X PUT --data-binary @snapshot-image.tar '%s'";
                echo "$CURL_CMD";
                sh -c "$CURL_CMD";
                echo "COMPLETED $CHECKPOINT_DIR"
                """.formatted(url);
        var execWatch = client.pods().withName(podName)
                .inContainer(containerName)
                .writingOutput(out)
                .writingError(out)
                .usingListener(new MyPodExecListener(logFuture, out))
                .exec("/bin/sh", "-c", cmd);
        try (execWatch) {
            String resultLog = logFuture.get(300, TimeUnit.SECONDS);
            LOGGER.info("KFSnapshot '{}': log \n{}",
                    kfsName, resultLog);
            Pattern pattern = Pattern.compile("COMPLETED (.*)\\n");
            Matcher matcher = pattern.matcher(resultLog);
            matcher.find();
            var path = matcher.group(1);
            spec.setSnapshotPath(path);
            double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
            status.setSnapshotDuration(totalTime);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public DeleteControl cleanup(KFSnapshot kfSnapshot,
                                 Context<KFSnapshot> context) {
        LOGGER.info("delete KFSnapshot {}", kfSnapshot.getMetadata().getName());
        var status = kfSnapshot.getStatus();
        if (Objects.equals(status.getState(), KFSnapshotStatus.COMPLETED)) {
            snapshotRepo.delete(kfSnapshot);
        }
        return DeleteControl.defaultDelete();
    }

    private static class MyPodExecListener implements ExecListener {
        //        CountDownLatch execLatch;
        CompletableFuture<String> log;
        ByteArrayOutputStream out;

        public MyPodExecListener(CompletableFuture<String> log,
                                 ByteArrayOutputStream out) {
            this.log = log;
            this.out = out;
        }

        @Override
        public void onOpen() {
        }

        @Override
        public void onFailure(Throwable t, Response failureResponse) {
            log.completeExceptionally(t);
        }

        @Override
        public void onClose(int code, String reason) {
            LOGGER.debug("Exit with: {} and with reason: {}", code, reason);
            log.complete(out.toString());
        }
    }
}
