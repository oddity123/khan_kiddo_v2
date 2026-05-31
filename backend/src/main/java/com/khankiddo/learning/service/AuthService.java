package com.khankiddo.learning.service;

import com.khankiddo.learning.dto.*;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    RegisterResponse register(RegisterRequest request);

    UserProfileDto getCurrentUserProfile();
}
