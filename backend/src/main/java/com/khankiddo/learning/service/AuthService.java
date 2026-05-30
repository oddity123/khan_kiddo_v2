package com.khankiddo.learning.service;

import com.khankiddo.learning.dto.LoginRequest;
import com.khankiddo.learning.dto.LoginResponse;
import com.khankiddo.learning.dto.UserProfileDto;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    UserProfileDto getCurrentUserProfile();
}
