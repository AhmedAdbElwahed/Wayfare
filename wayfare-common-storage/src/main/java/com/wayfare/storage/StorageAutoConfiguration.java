package com.wayfare.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Active only where {@code wayfare.storage.endpoint} is configured. */
@AutoConfiguration
@ConditionalOnProperty("wayfare.storage.endpoint")
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(StorageAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public ObjectStorage objectStorage(StorageProperties props) {
        return new ObjectStorage(props);
    }

    /**
     * Bucket setup at startup. A MinIO that's briefly down must not stop the
     * service booting (uploads are one feature among many), so this logs and
     * carries on; the next restart or a manual bucket create fixes it.
     */
    @Bean
    public ApplicationRunner ensureBucketsRunner(ObjectStorage storage) {
        return args -> {
            try {
                storage.ensureBuckets();
            } catch (Exception e) {
                log.warn("Could not verify MinIO buckets at startup: {}", e.toString());
            }
        };
    }
}
