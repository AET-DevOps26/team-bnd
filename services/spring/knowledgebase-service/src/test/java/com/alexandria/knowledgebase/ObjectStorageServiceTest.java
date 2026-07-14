package com.alexandria.knowledgebase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceTest {

    @Mock
    private S3Client s3Client;

    private ObjectStorageService service;

    @BeforeEach
    void setup() {
        service = new ObjectStorageService(s3Client);
        ReflectionTestUtils.setField(service, "bucket", "default-bucket");
    }

    @Test
    void unit_kb_bucketExistsTrue() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        assertThat(service.bucketExists("b")).isTrue();
    }

    @Test
    void unit_kb_bucketExistsFalseOnNoSuchBucket() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.builder().build());
        assertThat(service.bucketExists("b")).isFalse();
    }

    @Test
    void unit_kb_createBucketOnlyWhenMissing() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.builder().build());
        service.createBucket("b");
        verify(s3Client).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void unit_kb_createBucketSkipsWhenPresent() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        service.createBucket("b");
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void unit_kb_deleteBucketOnlyWhenPresent() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenReturn(HeadBucketResponse.builder().build());
        service.deleteBucket("b");
        verify(s3Client).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    void unit_kb_deleteBucketSkipsWhenMissing() {
        when(s3Client.headBucket(any(HeadBucketRequest.class))).thenThrow(NoSuchBucketException.builder().build());
        service.deleteBucket("b");
        verify(s3Client, never()).deleteBucket(any(DeleteBucketRequest.class));
    }

    @Test
    void unit_kb_uploadUsesDefaultBucket() {
        MultipartFile file = new MockMultipartFile("f", "a.pdf", "application/pdf", "hi".getBytes());
        service.upload("key", file);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void unit_kb_uploadWrapsIoException() throws IOException {
        MultipartFile file = new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[0]) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("boom");
            }
        };
        assertThatThrownBy(() -> service.upload("b", "key", file)).isInstanceOf(RuntimeException.class).hasMessageContaining("Failed to upload");
    }

    @Test
    void unit_kb_downloadReturnsBytes() {
        ResponseBytes<GetObjectResponse> bytes = ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "hi".getBytes());
        when(s3Client.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class))).thenReturn(bytes);

        assertThat(service.download("key")).isEqualTo("hi".getBytes());
    }

    @Test
    void unit_kb_deleteUsesDefaultBucket() {
        service.delete("key");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}
