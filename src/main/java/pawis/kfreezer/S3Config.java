package pawis.kfreezer;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "kfreezer.s3",
        namingStrategy = ConfigMapping.NamingStrategy.VERBATIM)
public interface S3Config {
    String url();
    String bucket();
    String accessKey();
    String secretKey();
    @WithDefault("true")
    boolean createBucket();
}
