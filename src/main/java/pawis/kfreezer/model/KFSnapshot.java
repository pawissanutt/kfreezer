package pawis.kfreezer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("kfreezer.pawissanutt.github.io")
@Version("v1")
@ShortNames("kfs")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KFSnapshot extends CustomResource<KFSnapshotSpec, KFSnapshotStatus> implements Namespaced {

    @Override
    protected KFSnapshotSpec initSpec() {
        return new KFSnapshotSpec();
    }

    @Override
    protected KFSnapshotStatus initStatus() {
        return new KFSnapshotStatus();
    }
}
