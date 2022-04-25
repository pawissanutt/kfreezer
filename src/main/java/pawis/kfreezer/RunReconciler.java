package pawis.kfreezer;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.quarkiverse.operatorsdk.bundle.runtime.CSVMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pawis.kfreezer.model.KFRun;
import pawis.kfreezer.model.KFRunStatus;
import pawis.kfreezer.model.KFSnapshot;

import javax.inject.Inject;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@CSVMetadata(permissionRules = {
        @CSVMetadata.PermissionRule(
                apiGroups = "kfreezer.pawissanutt.github.io",
                resources = "KFRun"
        )
})
@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE)
public class RunReconciler implements Reconciler<KFRun>, Cleaner<KFRun> {
    private static final Logger LOGGER = LoggerFactory.getLogger( RunReconciler.class );

    @Inject
    KubernetesClient client;

    @Override
    public DeleteControl cleanup(KFRun kfRun, Context<KFRun> context) {
        LOGGER.info("delete KFRun {}", kfRun.getMetadata().getName());
        return DeleteControl.defaultDelete();
    }

    @Override
    public UpdateControl<KFRun> reconcile(KFRun kfRun, Context<KFRun> context) throws Exception {
        var spec = kfRun.getSpec();
        var status = kfRun.getStatus();
        LOGGER.info("get new KFRun {}, snapshot: {}, new job: {}",
                kfRun.getMetadata().getName(),
                spec.getSnapshot(),
                spec.getJobName());
        KFSnapshot snapshot = client.resources(KFSnapshot.class)
                .withName(spec.getSnapshot()).get();
        if (snapshot == null) {
            status.setState(KFRunStatus.FAILED);
            status.setError("Not found KFSnapshot with name '%s'"
                    .formatted(spec.getSnapshot()));
            return UpdateControl.updateStatus(kfRun);
        } else {
            return createJob(kfRun, snapshot);
        }
    }

    private UpdateControl<KFRun> createJob(KFRun run,
                                           KFSnapshot snapshot) {

        return UpdateControl.updateStatus(run);
    }
}
