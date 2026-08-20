package com.AudioTracking.Platform.storage;

import com.AudioTracking.Platform.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Verifies R2StorageService correctly translates StorageService calls into S3 SDK calls (bucket,
// key, content type/length all wired through correctly) and never lets a raw SDK exception
// escape — everything above this class only ever sees StorageException. No real R2 connection
// is needed or made here: S3Client/S3Presigner are mocked entirely.
@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    private static final String BUCKET = "test-bucket";

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;

    private R2StorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new R2StorageService(s3Client, s3Presigner, BUCKET);
    }

    @Test
    void upload_sendsCorrectBucketKeyContentTypeAndLength() {
        byte[] data = "audio-bytes".getBytes();

        storageService.upload("users/1/assets/2/file.wav", new ByteArrayInputStream(data), data.length, "audio/wav");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        org.mockito.Mockito.verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();
        assertThat(request.bucket()).isEqualTo(BUCKET);
        assertThat(request.key()).isEqualTo("users/1/assets/2/file.wav");
        assertThat(request.contentType()).isEqualTo("audio/wav");
        assertThat(request.contentLength()).isEqualTo((long) data.length);
    }

    @Test
    void upload_sdkThrows_wrapsInStorageException_notTheRawSdkException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkException.builder().message("network down").build());

        assertThatThrownBy(() -> storageService.upload("key", new ByteArrayInputStream(new byte[0]), 0, "audio/wav"))
                .isInstanceOf(StorageException.class)
                .hasCauseInstanceOf(SdkException.class);
    }

    @Test
    void delete_sendsCorrectBucketAndKey() {
        storageService.delete("users/1/assets/2/file.wav");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        org.mockito.Mockito.verify(s3Client).deleteObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo("users/1/assets/2/file.wav");
    }

    @Test
    void delete_sdkThrows_wrapsInStorageException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkException.builder().message("network down").build());

        assertThatThrownBy(() -> storageService.delete("key"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void generatePresignedDownloadUrl_returnsUrlFromPresigner_withRequestedExpiration() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://r2.example.com/users/1/assets/2/file.wav?sig=abc"));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        URI result = storageService.generatePresignedDownloadUrl("users/1/assets/2/file.wav", Duration.ofMinutes(15));

        assertThat(result.toString()).isEqualTo("https://r2.example.com/users/1/assets/2/file.wav?sig=abc");

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        org.mockito.Mockito.verify(s3Presigner).presignGetObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(15));
        assertThat(requestCaptor.getValue().getObjectRequest().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().getObjectRequest().key()).isEqualTo("users/1/assets/2/file.wav");
    }

    @Test
    void generatePresignedDownloadUrl_sdkThrows_wrapsInStorageException() {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenThrow(SdkException.builder().message("network down").build());

        assertThatThrownBy(() -> storageService.generatePresignedDownloadUrl("key", Duration.ofMinutes(15)))
                .isInstanceOf(StorageException.class);
    }
}
