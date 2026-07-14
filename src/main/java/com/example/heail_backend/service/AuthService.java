package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.repository.*;
import com.example.heail_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository         userRepo;
    private final OrganisationRepository orgRepo;
    private final RefreshTokenRepository refreshRepo;
    private final OtpTokenRepository     otpRepo;
    private final PasswordEncoder        encoder;
    private final JwtService             jwtService;
    private final EmailService           emailService;

    /* ── REGISTER ──────────────────────────────────────────────── */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email already registered");

        Organisation org = null;

        if ("ORG_ADMIN".equalsIgnoreCase(req.getRole())) {
            if (req.getOrganisationName() == null || req.getOrganisationName().isBlank())
                throw new IllegalArgumentException("organisationName is required for ORG_ADMIN");

            Organisation newOrg = new Organisation();
            newOrg.setName(req.getOrganisationName());
            newOrg.setIndustry(req.getIndustry());
            newOrg.setSizeCategory(req.getSizeCategory());
            org = orgRepo.save(newOrg);

        } else if ("EMPLOYEE".equalsIgnoreCase(req.getRole()) || "LEADER".equalsIgnoreCase(req.getRole())) {
            if (req.getOrganisationId() == null)
                throw new IllegalArgumentException("organisationId is required for EMPLOYEE / LEADER");
            org = orgRepo.findById(UUID.fromString(req.getOrganisationId()))
                    .orElseThrow(() -> new IllegalArgumentException("Organisation not found"));
        } else {
            throw new IllegalArgumentException("role must be ORG_ADMIN, EMPLOYEE, or LEADER");
        }

        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail().toLowerCase());
        user.setPasswordHash(encoder.encode(req.getPassword()));
        user.setRole(req.getRole().toUpperCase());
        user.setRespondentLevel(req.getRespondentLevel());
        user.setOrganisation(org);
        user = userRepo.save(user);

        return buildAuthResponse(user);
    }

    /* ── LOGIN ─────────────────────────────────────────────────── */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!encoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid email or password");

        return buildAuthResponse(user);
    }

    /* ── REFRESH ───────────────────────────────────────────────── */
    @Transactional
    public AuthResponse refresh(RefreshRequest req) {
        RefreshToken rt = refreshRepo.findByToken(req.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (rt.isRevoked())
            throw new IllegalArgumentException("Refresh token has been revoked");

        if (rt.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Refresh token has expired");

        rt.setRevoked(true);
        refreshRepo.save(rt);

        return buildAuthResponse(rt.getUser());
    }

    /* ── FORGOT PASSWORD ───────────────────────────────────────── */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        userRepo.findByEmail(req.getEmail().toLowerCase()).ifPresent(user -> {
            otpRepo.invalidateAllByEmail(req.getEmail().toLowerCase());

            String otp = String.format("%06d", new Random().nextInt(1_000_000));

            OtpToken otpToken = new OtpToken();
            otpToken.setEmail(req.getEmail().toLowerCase());
            otpToken.setOtp(otp);
            otpToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));
            otpToken.setUsed(false);
            otpRepo.save(otpToken);

            emailService.sendOtp(req.getEmail(), otp);
        });
    }

    /* ── RESET PASSWORD ────────────────────────────────────────── */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        OtpToken token = otpRepo.findTopByEmailOrderByCreatedAtDesc(req.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("No OTP found for this email"));

        if (token.isUsed())
            throw new IllegalArgumentException("OTP already used");

        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("OTP has expired");

        if (!token.getOtp().equals(req.getOtp()))
            throw new IllegalArgumentException("Invalid OTP");

        User user = userRepo.findByEmail(req.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPasswordHash(encoder.encode(req.getNewPassword()));
        userRepo.save(user);

        otpRepo.invalidateAllByEmail(req.getEmail().toLowerCase());
        refreshRepo.revokeAllByUserId(user.getId());
    }

    /* ── Private helpers ───────────────────────────────────────── */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                Map.of(
                        "userId", user.getId().toString(),
                        "role",   user.getRole(),
                        "name",   user.getName()
                )
        );

        String refreshValue = jwtService.generateRefreshTokenValue();

        RefreshToken rt = new RefreshToken();
        rt.setToken(refreshValue);
        rt.setUser(user);
        rt.setExpiresAt(LocalDateTime.now().plusDays(30));
        rt.setRevoked(false);
        refreshRepo.save(rt);

        Organisation org = user.getOrganisation();

        AuthResponse.UserDto userDto = new AuthResponse.UserDto();
        userDto.setId(user.getId());
        userDto.setName(user.getName());
        userDto.setEmail(user.getEmail());
        userDto.setRole(user.getRole());
        userDto.setRespondentLevel(user.getRespondentLevel());
        userDto.setOrganisationId(org != null ? org.getId().toString() : null);
        userDto.setOrganisationName(org != null ? org.getName() : null);

        AuthResponse response = new AuthResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshValue);
        response.setExpiresIn(jwtService.accessTokenExpirySeconds());
        response.setUser(userDto);
        return response;
    }
}
