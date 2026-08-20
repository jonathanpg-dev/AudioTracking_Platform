package com.AudioTracking.Platform.controller;

import com.AudioTracking.Platform.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// The one thing AssetFileIntegrationTest's MockMvc setup structurally cannot prove: that
// spring.servlet.multipart.max-file-size (derived from storage.max-file-size-mb) actually rejects
// an oversized upload. @SpringBootTest's default WebEnvironment.MOCK never starts a real servlet
// container, so MockMvc bypasses container-level multipart size enforcement entirely there. This
// class boots a real embedded Tomcat (RANDOM_PORT) and drives it over real HTTP instead, with the
// limit dialed down to 1MB via @TestPropertySource so proving the point doesn't require pushing
// tens of megabytes through the test.
//
// A plain RestTemplate is used rather than Boot's TestRestTemplate: the latter needs a
// RestTemplateBuilder bean from spring-boot-restclient, which isn't on this project's classpath
// (only spring-boot-starter-webmvc is), and pulling in a new dependency just to support one test
// would be exactly the disproportionate testing-infrastructure change this test class exists to
// avoid. Its error handler is replaced with a no-op so 4xx/5xx responses come back as a normal
// ResponseEntity instead of an exception, matching what MockMvc-based tests get for free.
//
// StorageService is still mocked -- this is only about the servlet layer rejecting the request
// before it would ever reach AssetService/StorageService, not about real R2 connectivity (that's
// covered separately by R2StorageServiceTest against a mocked S3 SDK).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "storage.max-file-size-mb=1",
        // Left generously larger than storage.max-file-size-mb on purpose, unlike the 1:1 coupling
        // in application.properties: max-swallow-size is how much of an oversized request body
        // Tomcat will read-and-discard before it can send back a clean error response. With no
        // headroom above the 1MB limit here, the ~500KB overflow this test's oversized upload
        // creates doesn't fit in that swallow budget, so Tomcat resets the connection outright
        // instead of responding with a real HTTP status -- a raw I/O error on the client side, not
        // the clean 4xx this test is actually trying to verify. Production's much larger 4096MB
        // default doesn't hit this in practice for any realistically-sized accidental overshoot.
        "server.tomcat.max-http-form-post-size=10MB",
        "server.tomcat.max-swallow-size=10MB"
})
class AssetFileUploadSizeLimitIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StorageService storageService;

    private final RestTemplate restTemplate = nonThrowingRestTemplate();

    private String token;
    private String assetId;

    private static RestTemplate nonThrowingRestTemplate() {
        RestTemplate rt = new RestTemplate();
        rt.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false; // let 4xx/5xx come back as a normal ResponseEntity, not an exception
            }
            // handleError() is a no-op default method on the interface; never invoked since
            // hasError() always returns false.
        });
        return rt;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @BeforeEach
    void setUp() {
        long suffix = System.nanoTime();
        String username = "sizeLimit" + suffix;
        String email = username + "@example.com";

        restTemplate.postForEntity(url("/api/v1/auth/register"), jsonEntity("""
                {"username":"%s","email":"%s","password":"password123"}
                """.formatted(username, email)), String.class);

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(url("/api/v1/auth/login"), jsonEntity("""
                {"username":"%s","password":"password123"}
                """.formatted(username)), String.class);
        token = readField(loginResponse.getBody(), "token");

        HttpEntity<String> createRequest = new HttpEntity<>("""
                {"title":"Size limit test asset","assetType":"BEAT"}
                """, authHeaders(MediaType.APPLICATION_JSON));
        ResponseEntity<String> createResponse = restTemplate.postForEntity(url("/api/v1/assets"), createRequest, String.class);
        assetId = readField(createResponse.getBody(), "id");
    }

    @Test
    void uploadExceedingConfiguredLimit_isRejected_beforeReachingStorage() {
        // ~1.46MB: comfortably past the 1MB limit configured above, but well under embedded
        // Tomcat's own default connector-level post-size cap, so this test proves the
        // application's configured limit specifically, not an unrelated container default.
        byte[] oversized = validWavBytes(1_536_000);

        ResponseEntity<String> response = upload(oversized, "big.wav");

        // Asserted broadly rather than pinned to exactly 400: depending on exactly where the
        // servlet container trips the limit, the response can surface either as
        // GlobalExceptionHandler's mapped 400 or a raw container-level 413 -- both are a correct
        // rejection of the oversized request, and the only thing genuinely under test here.
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        verify(storageService, never()).upload(anyString(), any(), org.mockito.ArgumentMatchers.anyLong(), anyString());
    }

    @Test
    void uploadWithinConfiguredLimit_stillSucceeds() {
        byte[] withinLimit = validWavBytes(512_000); // well under the 1MB limit configured above

        ResponseEntity<String> response = upload(withinLimit, "ok.wav");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(storageService).upload(anyString(), any(), eq((long) withinLimit.length), anyString());
    }

    private byte[] validWavBytes(int size) {
        byte[] bytes = new byte[size];
        byte[] header = "RIFF1234WAVEfmt ".getBytes();
        System.arraycopy(header, 0, bytes, 0, header.length);
        return bytes;
    }

    private ResponseEntity<String> upload(byte[] bytes, String filename) {
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, authHeaders(MediaType.MULTIPART_FORM_DATA));
        return restTemplate.postForEntity(url("/api/v1/assets/" + assetId + "/file"), request, String.class);
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private HttpHeaders authHeaders(MediaType contentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private String readField(String json, String field) {
        try {
            return objectMapper.readTree(json).get(field).asText();
        } catch (Exception e) {
            throw new RuntimeException("Could not parse field '" + field + "' from response: " + json, e);
        }
    }
}
