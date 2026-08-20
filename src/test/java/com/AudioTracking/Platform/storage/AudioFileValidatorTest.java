package com.AudioTracking.Platform.storage;

import com.AudioTracking.Platform.exception.InvalidFileException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioFileValidatorTest {

    @Test
    void emptyFile_isRejected() {
        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", new byte[0]);
        assertThatThrownBy(() -> AudioFileValidator.validate(file)).isInstanceOf(InvalidFileException.class);
    }

    @Test
    void unsupportedExtension_isRejected() {
        MultipartFile file = new MockMultipartFile("file", "beat.ogg", "audio/ogg", "fake".getBytes());
        assertThatThrownBy(() -> AudioFileValidator.validate(file)).isInstanceOf(InvalidFileException.class);
    }

    @Test
    void noExtensionAtAll_isRejected() {
        MultipartFile file = new MockMultipartFile("file", "beat", "application/octet-stream", "fake".getBytes());
        assertThatThrownBy(() -> AudioFileValidator.validate(file)).isInstanceOf(InvalidFileException.class);
    }

    @Test
    void validExtension_butContentDoesNotMatchSignature_isRejected() {
        // This is the actual point of signature checking: a client renaming any file to
        // "beat.wav" must not be enough to have it accepted.
        MultipartFile file = new MockMultipartFile("file", "beat.wav", "audio/wav", "not a real wav file".getBytes());
        assertThatThrownBy(() -> AudioFileValidator.validate(file)).isInstanceOf(InvalidFileException.class);
    }

    @Test
    void validWav_isAccepted() {
        byte[] bytes = "RIFF1234WAVEfmt ".getBytes();
        MultipartFile file = new MockMultipartFile("file", "beat.WAV", "audio/wav", bytes); // uppercase extension too
        var result = AudioFileValidator.validate(file);
        assertThat(result.extension()).isEqualTo("wav");
        assertThat(result.contentType()).isEqualTo("audio/wav");
    }

    @Test
    void validFlac_isAccepted() {
        byte[] bytes = {'f', 'L', 'a', 'C', 1, 2, 3, 4};
        MultipartFile file = new MockMultipartFile("file", "beat.flac", "audio/flac", bytes);
        var result = AudioFileValidator.validate(file);
        assertThat(result.extension()).isEqualTo("flac");
    }

    @Test
    void validM4a_isAccepted() {
        byte[] bytes = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'M', '4', 'A', ' '};
        MultipartFile file = new MockMultipartFile("file", "beat.m4a", "audio/mp4", bytes);
        var result = AudioFileValidator.validate(file);
        assertThat(result.extension()).isEqualTo("m4a");
    }

    @Test
    void validMp3WithId3Tag_isAccepted() {
        byte[] bytes = {'I', 'D', '3', 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MultipartFile file = new MockMultipartFile("file", "beat.mp3", "audio/mpeg", bytes);
        var result = AudioFileValidator.validate(file);
        assertThat(result.extension()).isEqualTo("mp3");
    }

    @Test
    void validMp3WithBareFrameSync_isAccepted() {
        byte[] bytes = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0x00};
        MultipartFile file = new MockMultipartFile("file", "beat.mp3", "audio/mpeg", bytes);
        var result = AudioFileValidator.validate(file);
        assertThat(result.extension()).isEqualTo("mp3");
    }
}
