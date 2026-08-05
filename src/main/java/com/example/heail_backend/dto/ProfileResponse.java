package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class ProfileResponse {
    String name;
    String email;
    String mobile;
    String city;
    String country;
    String role;
}
