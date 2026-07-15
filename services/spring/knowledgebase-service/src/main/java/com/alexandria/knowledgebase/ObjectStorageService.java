package com.alexandria.knowledgebase;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Wrapper over the AWS S3 client for the SeaweedFS object store.
 *
 * <p>Handles bucket lifecycle and object upload/download/delete against the bucket
 * configured by {@code app.config.s3-bucket}. Each operation is timed and counted
 * ({@code s3.operation.duration}, {@code s3.operations}, tagged by operation and
 * outcome) so object storage latency and errors surface in Prometheus.
 *
 * <p>Based on https://www.baeldung.com/java-aws-s3 and
 * https://dev.to/sachithmayantha/seamless-file-storage-integrating-aws-s3-with-spring-boot-3045
 */
@Component
public class ObjectStorageService {
    @Value("${app.config.s3-bucket}")
    String bucket;

    private final S3Client s3Client;
    private final MeterRegistry meterRegistry;

    public ObjectStorageService(S3Client s3Client, MeterRegistry meterRegistry) {
        this.s3Client = s3Client;
        this.meterRegistry = meterRegistry;
    }

    private <T> T tracked(String operation, Supplier<T> call) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            return call.get();
        } catch (RuntimeException e) {
            outcome = "error";
            throw e;
        } finally {
            sample.stop(meterRegistry.timer("s3.operation.duration", "operation", operation, "outcome", outcome));
            meterRegistry.counter("s3.operations", "operation", operation, "outcome", outcome).increment();
        }
    }

    public boolean bucketExists(String bucket) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    public void createBucket(String bucket) {
        if (!bucketExists(bucket)) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }

    public void deleteBucket(String bucket) {
        if (bucketExists(bucket)) {
            s3Client.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build());
        }
    }

    public void upload(String bucket, String key, MultipartFile file) {
        tracked("upload", () -> {
            try {
                PutObjectRequest putRequest = PutObjectRequest.builder().bucket(bucket).key(key).build();
                RequestBody requestBody = RequestBody.fromBytes(file.getBytes());
                s3Client.putObject(putRequest, requestBody);
                return null;
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload file.", e);
            }
        });
    }

    public void upload(String key, MultipartFile file) {
        upload(bucket, key, file);
    }

    public byte[] download(String bucket, String key) {
        return tracked("download", () -> {
            GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
            return s3Client.getObject(getRequest, ResponseTransformer.toBytes()).asByteArray();
        });
    }

    public byte[] download(String key) {
        return download(bucket, key);
    }

    public void delete(String bucket, String key) {
        tracked("delete", () -> {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder().bucket(bucket).key(key).build();
            s3Client.deleteObject(deleteRequest);
            return null;
        });
    }

    public void delete(String key) {
        delete(bucket, key);
    }
}
