package pawis.kfreezer;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import io.vertx.core.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pawis.kfreezer.model.KFRun;
import pawis.kfreezer.model.KFRunStatus;
import pawis.kfreezer.model.KFSnapshot;
import pawis.kfreezer.model.KFSnapshotStatus;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Objects;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@CSVMetadata(permissionRules = {@CSVMetadata.PermissionRule(apiGroups = "kfreezer.pawissanutt.github.io", resources = "KFRun")})
@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE)
@ApplicationScoped
public class RunReconciler implements Reconciler<KFRun>, Cleaner<KFRun> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RunReconciler.class);

    @Inject
    KubernetesClient client;
    @Inject
    SnapshotRepository snapshotRepo;

    @Override
    public DeleteControl cleanup(KFRun kfRun, Context<KFRun> context) {
        LOGGER.info("delete KFRun {}", kfRun.getMetadata().getName());
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<KFRun> reconcile(KFRun kfRun, Context<KFRun> context) throws Exception {

        var spec = kfRun.getSpec();
        var status = kfRun.getStatus();
        if (Objects.equals(status.getState(), KFRunStatus.COMPLETED)) {
            return UpdateControl.noUpdate();
        }
        LOGGER.info("get new KFRun {}, snapshot: {}, new job: {}", kfRun.getMetadata().getName(), spec.getSnapshot(), spec.getJobName());

        KFSnapshot snapshot = client.resources(KFSnapshot.class).withName(spec.getSnapshot()).get();
        if (snapshot==null) {
            status.setState(KFRunStatus.FAILED);
            status.setError("Not found KFSnapshot with name '%s'".formatted(spec.getSnapshot()));
            return UpdateControl.updateStatus(kfRun);
        } else if (!Objects.equals(snapshot.getStatus().getState(), KFSnapshotStatus.COMPLETED)) {
            LOGGER.info("KFRun '{}': cannot creating a new job because the snapshot '{}' is not completed yet.", kfRun.getMetadata().getName(), snapshot.getMetadata().getName());
            return UpdateControl.noUpdate();
        } else {
            return createJob(kfRun, snapshot);
        }
    }

    private UpdateControl<KFRun> createJob(KFRun run, KFSnapshot snapshot) {
        LOGGER.info("Creating a job from KFRun '{}' and KFSnapshot '{}'", run.getMetadata().getName(), snapshot.getMetadata().getName());
        JobSpec jobSpec = Json.decodeValue(snapshot.getStatus().getJobSpecs(), JobSpec.class);
        var labels = jobSpec.getTemplate().getMetadata().getLabels();
        labels.remove("controller-uid");
        labels.remove("job-name");
        jobSpec.setSelector(null);
        var snapshotPath = snapshot.getSpec().getSnapshotPath();
        PodSpec podSpec = jobSpec.getTemplate().getSpec();
        var initContainers = podSpec.getInitContainers();
        var snapshotUrl = snapshotRepo.getUrl(snapshot);

        Volume volume = new VolumeBuilder().withName("snapshot-image").withNewEmptyDir().endEmptyDir().build();
        VolumeMount volumeMount = new VolumeMountBuilder().withMountPath(snapshotPath).withName("snapshot-image").build();
        var con = new ContainerBuilder().withName("snapshot-loader").withImage("rancher/curl").withCommand("sh", "-c").withArgs("""
                curl -o "%s/image.tar" "%s";
                tar -xvzf "%s/image.tar" -C "%s"; 
                """.formatted(snapshotPath, snapshotUrl, snapshotPath, snapshotPath)).addToVolumeMounts(volumeMount).build();
        podSpec.getVolumes().add(volume);
        podSpec.getContainers().get(0).getVolumeMounts().add(volumeMount);
        initContainers.add(con);
        Job job = new JobBuilder().withNewMetadata().withName(run.getSpec().getJobName()).withNamespace(client.getNamespace()).endMetadata().withSpec(jobSpec).build();
        job.addOwnerReference(run);
        LOGGER.info("creating job \n{}", Json.encodePrettily(job));
        client.batch().v1().jobs().create(job);
        run.getStatus().setState(KFRunStatus.COMPLETED);

        return UpdateControl.updateStatus(run);
    }
}
