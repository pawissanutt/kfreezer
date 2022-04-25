package pawis.kfreezer;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import pawis.kfreezer.model.KFRun;

public class JobDependent extends KubernetesDependentResource<Job, KFRun>
    implements Creator<Job, KFRun> {
    public JobDependent() {
        super(Job.class);
    }
}
