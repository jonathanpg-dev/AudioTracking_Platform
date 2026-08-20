package com.AudioTracking.Platform.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Every handler in GlobalExceptionHandler is a plain method (exception in, ResponseEntity<ApiError>
// out) with no Spring context dependency, so it's exercised directly here rather than only
// indirectly through whichever controller happens to trigger it. This is what actually pins down
// the status code + body shape for each exception type in one place, and is where a
// StorageException's wrapped SDK details being kept out of the response body gets its dedicated
// assertion, not just an incidental one buried in a storage-specific test.
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void resourceNotFound_returns404_withMessage() {
        ResponseEntity<ApiError> response = handler.handleNotFound(new ResourceNotFoundException("Asset with id 'x' not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("Asset with id 'x' not found");
    }

    @Test
    void duplicateResource_returns409() {
        ResponseEntity<ApiError> response = handler.handleDuplicate(new DuplicateResourceException("username already taken"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).isEqualTo("username already taken");
    }

    @Test
    void authenticationException_returns401_withGenericMessage_neverTheRawOne() {
        // The raw exception message is deliberately discarded in favor of a fixed generic one, so
        // a caller can never distinguish "wrong username" from "wrong password" (username
        // enumeration).
        ResponseEntity<ApiError> response = handler.handleAuthentication(new BadCredentialsException("user 'bob' not found"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Invalid username or password");
    }

    @Test
    void invalidGoogleToken_returns401_withSpecificMessage() {
        ResponseEntity<ApiError> response = handler.handleInvalidGoogleToken(new InvalidGoogleTokenException("Google token verification failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Google token verification failed");
    }

    @Test
    void validationFailure_returns400_withPerFieldErrors() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("createAssetRequest", "title", "must not be blank")
        ));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<ApiError> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
        assertThat(response.getBody().fieldErrors()).containsEntry("title", "must not be blank");
    }

    @Test
    void malformedRequestBody_returns400_withGenericMessage() {
        ResponseEntity<ApiError> response = handler.handleMalformedRequest(
                mock(org.springframework.http.converter.HttpMessageNotReadableException.class));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Malformed request body");
    }

    @Test
    void typeMismatch_returns400_namingTheOffendingParameter() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");

        ResponseEntity<ApiError> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Invalid value for parameter 'id'");
    }

    @Test
    void invalidFile_returns400_withValidatorMessage() {
        ResponseEntity<ApiError> response = handler.handleInvalidFile(new InvalidFileException("Unsupported audio format. Supported formats: mp3, wav, flac, m4a"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Unsupported audio format. Supported formats: mp3, wav, flac, m4a");
    }

    @Test
    void maxUploadSizeExceeded_returns400_withGenericMessage() {
        ResponseEntity<ApiError> response = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1_048_576));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).isEqualTo("Uploaded file exceeds the maximum allowed size");
    }

    @Test
    void storageException_returns502_neverLeakingTheWrappedSdkExceptionToTheClient() {
        RuntimeException sdkFailure = new RuntimeException("connection to r2.internal-bucket-name.example failed");
        ResponseEntity<ApiError> response = handler.handleStorage(new StorageException("Failed to upload object to storage", sdkFailure));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().message()).isEqualTo("A storage operation failed. Please try again.");
        assertThat(response.getBody().message()).doesNotContain("r2.internal-bucket-name.example");
    }

    @Test
    void methodNotSupported_returns405() {
        ResponseEntity<ApiError> response = handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void unexpectedException_returns500_withGenericMessage_neverTheRawOne() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(new RuntimeException("NullPointerException at line 42 in InternalRepository"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
    }
}
