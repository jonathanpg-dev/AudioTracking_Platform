package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.AuthResponse;
import com.AudioTracking.Platform.dto.GoogleLoginRequest;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.InvalidGoogleTokenException;
import com.AudioTracking.Platform.mapper.UserMapper;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplGoogleLoginTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(userRepository, userMapper, passwordEncoder,
                authenticationManager, jwtService, googleIdTokenVerifier);
    }

    private GoogleIdToken tokenWith(String subject, String email, Boolean emailVerified) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);

        GoogleIdToken idToken = mock(GoogleIdToken.class);
        when(idToken.getPayload()).thenReturn(payload);
        return idToken;
    }

    @Test
    void newGoogleEmail_createsNewUserWithGeneratedUsername() throws Exception {
        GoogleIdToken idToken = tokenWith("google-sub-1", "newperson@example.com", true);
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(idToken);
        when(userRepository.findByGoogleId("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("newperson@example.com")).thenReturn(Optional.empty());
        when(userRepository.existsByUsername("newperson")).thenReturn(false);

        User saved = new User();
        saved.setId(UUID.randomUUID());
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtService.generateToken(saved.getId())).thenReturn("jwt-123");

        AuthResponse response = authService.googleLogin(new GoogleLoginRequest("valid-token"));

        assertThat(response.token()).isEqualTo("jwt-123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("newperson");
        assertThat(created.getEmail()).isEqualTo("newperson@example.com");
        assertThat(created.getGoogleId()).isEqualTo("google-sub-1");
        assertThat(created.getPasswordHash()).isNull();
    }

    @Test
    void usernameCollision_appendsNumericSuffixUntilUnique() throws Exception {
        GoogleIdToken idToken = tokenWith("google-sub-2", "taken@example.com", true);
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(idToken);
        when(userRepository.findByGoogleId("google-sub-2")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.empty());
        // "taken" and "taken1" already exist; "taken2" is free
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        when(userRepository.existsByUsername("taken1")).thenReturn(true);
        when(userRepository.existsByUsername("taken2")).thenReturn(false);

        User saved = new User();
        saved.setId(UUID.randomUUID());
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(jwtService.generateToken(any())).thenReturn("jwt-x");

        authService.googleLogin(new GoogleLoginRequest("valid-token"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("taken2");
    }

    @Test
    void existingGoogleId_logsIntoSameUser_withoutCreatingOrLinking() throws Exception {
        GoogleIdToken idToken = tokenWith("google-sub-3", "returning@example.com", true);
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(idToken);

        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setUsername("returningUser");
        existing.setGoogleId("google-sub-3");
        when(userRepository.findByGoogleId("google-sub-3")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(existing.getId())).thenReturn("jwt-returning");

        AuthResponse response = authService.googleLogin(new GoogleLoginRequest("valid-token"));

        assertThat(response.token()).isEqualTo("jwt-returning");
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void matchingEmailOnPasswordAccount_linksGoogleId_toExistingUser_doesNotOverwriteUsername() throws Exception {
        GoogleIdToken idToken = tokenWith("google-sub-4", "linkme@example.com", true);
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(idToken);
        when(userRepository.findByGoogleId("google-sub-4")).thenReturn(Optional.empty());

        User existingPasswordUser = new User();
        existingPasswordUser.setId(UUID.randomUUID());
        existingPasswordUser.setUsername("originalUsername");
        existingPasswordUser.setEmail("linkme@example.com");
        existingPasswordUser.setPasswordHash("some-bcrypt-hash");
        when(userRepository.findByEmail("linkme@example.com")).thenReturn(Optional.of(existingPasswordUser));
        when(userRepository.save(existingPasswordUser)).thenReturn(existingPasswordUser);
        when(jwtService.generateToken(existingPasswordUser.getId())).thenReturn("jwt-linked");

        AuthResponse response = authService.googleLogin(new GoogleLoginRequest("valid-token"));

        assertThat(response.token()).isEqualTo("jwt-linked");
        assertThat(existingPasswordUser.getGoogleId()).isEqualTo("google-sub-4");
        assertThat(existingPasswordUser.getUsername()).isEqualTo("originalUsername"); // untouched
        assertThat(existingPasswordUser.getPasswordHash()).isEqualTo("some-bcrypt-hash"); // untouched, still password-loginable
        verify(userRepository, times(1)).save(existingPasswordUser);
    }

    @Test
    void emailNotVerified_throwsInvalidGoogleTokenException_andNeverTouchesRepository() throws Exception {
        GoogleIdToken idToken = tokenWith("google-sub-5", "unverified@example.com", false);
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(idToken);

        assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequest("valid-token")))
                .isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessageContaining("not verified");

        verify(userRepository, never()).findByGoogleId(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void missingEmailVerifiedClaim_treatedAsNotVerified() throws Exception {
        // Some tokens omit email_verified entirely (null) rather than sending false explicitly.
        GoogleIdToken idToken = tokenWith("google-sub-6", "noclaim@example.com", null);
        when(googleIdTokenVerifier.verify("valid-token")).thenReturn(idToken);

        assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequest("valid-token")))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }

    @Test
    void verifierReturnsNull_throwsInvalidGoogleTokenException() throws Exception {
        when(googleIdTokenVerifier.verify("garbage")).thenReturn(null);

        assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequest("garbage")))
                .isInstanceOf(InvalidGoogleTokenException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void verifierThrowsIOException_isTranslatedToInvalidGoogleTokenException() throws Exception {
        when(googleIdTokenVerifier.verify(anyString())).thenThrow(new java.io.IOException("cert fetch failed"));

        assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequest("whatever")))
                .isInstanceOf(InvalidGoogleTokenException.class);
    }
}
