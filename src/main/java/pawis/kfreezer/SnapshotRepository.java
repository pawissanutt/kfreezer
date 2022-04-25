package pawis.kfreezer;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import io.minio.http.Method;
import pawis.kfreezer.model.KFSnapshot;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class SnapshotRepository {
    @Inject
    MinioClient minioClient;
    @Inject
    S3Config s3Config;


    public String allocate(KFSnapshot snapshot) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(s3Config.bucket())
                    .object(snapshot.getMetadata().getUid() + "/image.tar")
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(KFSnapshot snapshot) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(s3Config.bucket())
                    .object(snapshot.getMetadata().getUid() + "/image.tar")
                    .build());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
