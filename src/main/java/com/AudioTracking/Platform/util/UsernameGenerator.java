package com.AudioTracking.Platform.util;

import com.AudioTracking.Platform.repository.UserRepository;
import org.springframework.stereotype.Component;

// Derives a unique username from an email's local-part -- shared by every path that creates a
// User without the caller having chosen a username themselves: Google login
// (AuthServiceImpl#linkOrCreateGoogleUser) and client account auto-provisioning
// (ClientServiceImpl#provisionClientOnlyUser). Extracted rather than duplicated once a second
// caller needed the exact same logic.
@Component
public class UsernameGenerator {

    private final UserRepository userRepository;

    public UsernameGenerator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String generateUniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.length() < 3) {
            base = base + "user"; // pad short local-parts (e.g. "ab") up to the 3-char minimum
        }
        if (base.length() > 26) {
            base = base.substring(0, 26); // leaves room for a numeric suffix, staying within the 30-char limit
        }

        String candidate = base;
        int suffix = 0;
        while (userRepository.existsByUsername(candidate)) {
            suffix++;
            candidate = base + suffix;
        }
        return candidate;
    }
}
