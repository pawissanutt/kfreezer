package pawis.kfreezer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("kfreezer.pawissanutt.github.io")
@Version("v1")
@ShortNames("kfr")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KFRun
        extends CustomResource<KFRunSpec, KFRunStatus>
        implements Namespaced {

    @Override
    protected KFRunSpec initSpec() {
        return new KFRunSpec();
    }

    @Override
    protected KFRunStatus initStatus() {
        return new KFRunStatus();
    }
}
