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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;


//@CSVMetadata(permissionRules = {
//        @CSVMetadata.PermissionRule(
//                apiGroups = "kfreezer.pawissanutt.github.io",
//                resources = "KFSnapshots"
//        ),
//        @CSVMetadata.PermissionRule(
//                apiGroups = "",
//                resources = {"pods", "pods/log"},
//                verbs = {"get", "list"}
//        ),
//        @CSVMetadata.PermissionRule(
//                apiGroups = "batch",
//                resources = {"jobs"},
//                verbs = {"get", "list", "create"}
//        ),
//        @CSVMetadata.PermissionRule(
//                apiGroups = "",
//                resources = "pods/exec",
//                verbs = {"create"}
//        )
//})
@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE)
public class SnapshotReconciler implements Reconciler<KFSnapshot>, Cleaner<KFSnapshot> {
    private static final Logger LOGGER = LoggerFactory.getLogger( SnapshotReconciler.class );

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
            if (status.getError() != null) {
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
        if (job == null) {
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
//        ByteArrayOutputStream error = new ByteArrayOutputStream();
        CountDownLatch execLatch = new CountDownLatch(1);
        var url = snapshotRepo.allocate(kfSnapshot);
        LOGGER.info("KFSnapshot '{}': presigned url: '{}'",
                kfsName, url);
        var cmd = """
                mkdir -p .snapshot-image;
                fastfreeze checkpoint --leave-running -i file:$PWD/.snapshot-image;
                tar -cvzf snapshot-image.tar .snapshot-image;
                ls -al;
                CURL_CMD="curl -sS -X PUT --data-binary @snapshot-image.tar '%s'";
                echo "$CURL_CMD";
                sh -c "$CURL_CMD";
                echo "COMPLETED"
                """.formatted(url);
        var execWatch = client.pods().withName(podName)
                .inContainer(containerName)
                .writingOutput(out)
                .redirectingError()
                .usingListener(new MyPodExecListener(execLatch))
                .exec("/bin/sh", "-c", cmd);
        try (execWatch) {
            boolean latchTerminationStatus = execLatch.await(5, TimeUnit.SECONDS);
            if (latchTerminationStatus) {
                var log = out.toString();
//                var errorLog = new String(execWatch.getError().readAllBytes());
                LOGGER.info("KFSnapshot '{}': log \n{}",
                        kfsName, log);
            }

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
        CountDownLatch execLatch;

        public MyPodExecListener(CountDownLatch execLatch) {
            this.execLatch = execLatch;
        }

        @Override
        public void onOpen() {
        }

        @Override
        public void onFailure(Throwable t, Response failureResponse) {
            execLatch.countDown();
        }

        @Override
        public void onClose(int i, String s) {
            execLatch.countDown();
        }
    }
}
