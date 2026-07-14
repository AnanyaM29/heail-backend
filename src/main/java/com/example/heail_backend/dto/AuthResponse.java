package com.example.heail_backend.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class AuthResponse {
    String accessToken;
    String refreshToken;
    long   expiresIn;
    UserDto user;

    @Data
    public static class UserDto {
        UUID   id;
        String name;
        String email;
        String role;
        String respondentLevel;
        String organisationId;
        String organisationName;
    }
}
