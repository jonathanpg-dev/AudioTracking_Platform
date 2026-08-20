package com.AudioTracking.Platform.storage;

import com.AudioTracking.Platform.exception.InvalidFileException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

// Validates an uploaded file before it ever reaches storage: not empty, extension is one of the
// supported formats, AND the file's actual bytes match that format's known signature. Checking
// only the filename extension would mean a client could rename anything (a script, an image) to
// "beat.wav" and have it accepted — the signature check is what makes this a real content check,
// not just a client-supplied label.
public final class AudioFileValidator {

    public record ValidatedAudioFile(String extension, String contentType) {
    }

    private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION = Map.of(
            "mp3", "audio/mpeg",
            "wav", "audio/wav",
            "flac", "audio/flac",
            "m4a", "audio/mp4"
    );

    private AudioFileValidator() {
    }

    public static ValidatedAudioFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file must not be empty");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (extension == null || !CONTENT_TYPE_BY_EXTENSION.containsKey(extension)) {
            throw new InvalidFileException("Unsupported audio format. Supported formats: mp3, wav, flac, m4a");
        }

        if (!matchesSignature(readHeader(file), extension)) {
            throw new InvalidFileException("File content does not match its extension");
        }

        return new ValidatedAudioFile(extension, CONTENT_TYPE_BY_EXTENSION.get(extension));
    }

    private static String extractExtension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private static byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(12);
        } catch (IOException e) {
            throw new InvalidFileException("Could not read uploaded file");
        }
    }

    private static boolean matchesSignature(byte[] header, String extension) {
        return switch (extension) {
            case "wav" -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E';
            case "flac" -> header.length >= 4
                    && header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C';
            case "m4a" -> header.length >= 8
                    && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p';
            // MP3 has no single universal signature: either an "ID3" tag header, or a bare MPEG
            // frame sync (11 set bits: 0xFF followed by the top 3 bits of the next byte also set).
            case "mp3" -> header.length >= 3 && (
                    (header[0] == 'I' && header[1] == 'D' && header[2] == '3')
                            || ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0)
            );
            default -> false;
        };
    }
}
