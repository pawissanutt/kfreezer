package pawis.kfreezer;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.quarkus.runtime.StartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

@ApplicationScoped
public class S3Initialization {

    private static final Logger LOGGER = LoggerFactory.getLogger( S3Initialization.class );
    S3Config s3Config;
    MinioClient minioClient;

    @Inject
    public S3Initialization(S3Config s3Config) {
        this.s3Config = s3Config;
        minioClient = MinioClient.builder()
                .endpoint(s3Config.url())
                .credentials(s3Config.accessKey(), s3Config.secretKey())
                .build();
    }

    @Produces
    public MinioClient minioClient() {
        return minioClient;
    }

    public void setup(@Observes StartupEvent event) throws Exception {
        var bucketExists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(s3Config.bucket())
                .build());
        if (!bucketExists) {
            LOGGER.info("Not found bucket '{}', creating a new bucket...",
                    s3Config.bucket());
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(s3Config.bucket())
                    .build());
            LOGGER.info("Create a new bucket successfully");
        }
    }

}
