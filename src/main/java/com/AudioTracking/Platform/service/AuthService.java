package com.AudioTracking.Platform.service;

import com.AudioTracking.Platform.dto.AuthResponse;
import com.AudioTracking.Platform.dto.LoginRequest;
import com.AudioTracking.Platform.dto.RegisterRequest;
import com.AudioTracking.Platform.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);
}
