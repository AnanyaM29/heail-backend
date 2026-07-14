package com.example.heail_backend.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter @Setter
public class JwtProperties {
    private String secret;
    private long   accessTokenExpiryMs  = 86_400_000L;    // 24 h
    private long   refreshTokenExpiryMs = 2_592_000_000L; // 30 d
}
