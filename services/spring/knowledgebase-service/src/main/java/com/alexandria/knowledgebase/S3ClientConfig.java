package com.alexandria.knowledgebase;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Builds the S3 client used to talk to the SeaweedFS object store.
 *
 * <p>Uses static credentials and an endpoint override with path-style access, which
 * SeaweedFS (and most S3-compatible stores that are not AWS) require. All values come from
 * {@code app.config.s3-*} properties.
 *
 * <p>Based on https://www.baeldung.com/java-aws-s3 and
 * https://dev.to/sachithmayantha/seamless-file-storage-integrating-aws-s3-with-spring-boot-3045
 */
@Configuration
public class S3ClientConfig {
    @Value("${app.config.s3-endpoint}")
    String endpoint;
    @Value("${app.config.s3-region}")
    String region;
    @Value("${app.config.s3-access-key}")
    String accessKey;
    @Value("${app.config.s3-secret-key}")
    String secretKey;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(region)).credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey))).forcePathStyle(true).build();
    }
}
