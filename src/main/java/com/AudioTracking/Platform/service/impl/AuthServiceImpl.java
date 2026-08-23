package com.AudioTracking.Platform.service.impl;

import com.AudioTracking.Platform.dto.AuthResponse;
import com.AudioTracking.Platform.dto.GoogleLoginRequest;
import com.AudioTracking.Platform.dto.LoginRequest;
import com.AudioTracking.Platform.dto.RegisterRequest;
import com.AudioTracking.Platform.dto.UserResponse;
import com.AudioTracking.Platform.entity.User;
import com.AudioTracking.Platform.exception.DuplicateResourceException;
import com.AudioTracking.Platform.exception.InvalidGoogleTokenException;
import com.AudioTracking.Platform.mapper.UserMapper;
import com.AudioTracking.Platform.repository.UserRepository;
import com.AudioTracking.Platform.security.CustomUserDetails;
import com.AudioTracking.Platform.security.JwtService;
import com.AudioTracking.Platform.service.AuthService;
import com.AudioTracking.Platform.util.UsernameGenerator;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final UsernameGenerator usernameGenerator;

    public AuthServiceImpl(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder,
                            AuthenticationManager authenticationManager, JwtService jwtService,
                            GoogleIdTokenVerifier googleIdTokenVerifier, UsernameGenerator usernameGenerator) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.usernameGenerator = usernameGenerator;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already taken");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(userDetails.getUser().getId());
        return new AuthResponse(token);
    }

    @Override
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(request.idToken());
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException("Could not verify Google token");
        }

        if (idToken == null) {
            throw new InvalidGoogleTokenException("Invalid or expired Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new InvalidGoogleTokenException("Google account email is not verified");
        }

        String googleId = payload.getSubject();
        String email = payload.getEmail();

        User user = userRepository.findByGoogleId(googleId)
                .orElseGet(() -> linkOrCreateGoogleUser(googleId, email));

        String token = jwtService.generateToken(user.getId());
        return new AuthResponse(token);
    }

    private User linkOrCreateGoogleUser(String googleId, String email) {
        return userRepository.findByEmail(email)
                .map(existing -> {
                    existing.setGoogleId(googleId);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> {
                    User user = new User();
                    user.setUsername(usernameGenerator.generateUniqueUsername(email));
                    user.setEmail(email);
                    user.setGoogleId(googleId);
                    return userRepository.save(user);
                });
    }
}
