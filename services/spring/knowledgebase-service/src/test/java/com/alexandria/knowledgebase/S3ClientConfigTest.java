package com.alexandria.knowledgebase;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

class S3ClientConfigTest {

    @Test
    void unit_kb_buildsS3ClientFromConfiguredValues() {
        S3ClientConfig config = new S3ClientConfig();
        ReflectionTestUtils.setField(config, "endpoint", "http://localhost:9000");
        ReflectionTestUtils.setField(config, "region", "eu-central-1");
        ReflectionTestUtils.setField(config, "accessKey", "key");
        ReflectionTestUtils.setField(config, "secretKey", "secret");

        try (S3Client client = config.s3Client()) {
            assertThat(client).isNotNull();
        }
    }
}
