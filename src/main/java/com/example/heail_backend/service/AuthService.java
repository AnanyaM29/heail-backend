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

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository         userRepo;
    private final RefreshTokenRepository refreshRepo;
    private final OtpTokenRepository     otpRepo;
    private final PasswordEncoder        encoder;
    private final JwtService             jwtService;
    private final EmailService           emailService;
    private final SmsService             smsService;

    /* ── REGISTER: SEND EMAIL + SMS VERIFICATION CODE ──────────────── */
    @Transactional
    public void sendRegistrationOtp(RegisterOtpRequest req) {
        String email = req.getEmail().toLowerCase();
        if (userRepo.existsByEmail(email))
            throw new IllegalArgumentException("Email already registered");

        otpRepo.invalidateAllByEmail(email);

        String otp = String.format("%06d", new Random().nextInt(1_000_000));

        OtpToken otpToken = new OtpToken();
        otpToken.setEmail(email);
        otpToken.setOtp(otp);
        otpToken.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        otpToken.setUsed(false);
        otpRepo.save(otpToken);

        emailService.sendRegistrationOtp(req.getEmail(), otp);
        smsService.sendOtp(req.getMobile(), otp);
    }

    /* ── REGISTER ──────────────────────────────────────────────── */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail()))
            throw new IllegalArgumentException("Email already registered");

        String emailLower = req.getEmail().toLowerCase();
        OtpToken token = otpRepo.findTopByEmailOrderByCreatedAtDesc(emailLower)
                .orElseThrow(() -> new IllegalArgumentException("Please request a verification code first"));

        if (token.isUsed())
            throw new IllegalArgumentException("This code has already been used — request a new one");

        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Code has expired");

        if (!token.getOtp().equals(req.getOtp()))
            throw new IllegalArgumentException("Incorrect code");

        // Only one type of account is created here — a bare "LEADER" account
        // with no organisation attached. Anything role/organisation-specific
        // (setting up an org round, its headcount, its industry) is deferred
        // to the buy-org flow, right before payment — see
        // OrgOrderService.setOrgDetails, which promotes this account to
        // ORG_ADMIN on demand. EMPLOYEE accounts are never self-registered;
        // they're always created by OrgOrderService.fulfil() from an invite.
        User user = new User();
        user.setName(req.getName());
        user.setEmail(req.getEmail().toLowerCase());
        user.setPasswordHash(encoder.encode(req.getPassword()));
        user.setRole("LEADER");
        user.setCity(req.getCity());
        user.setCountry(req.getCountry());
        user.setMobile(req.getMobile());
        user = userRepo.save(user);

        otpRepo.invalidateAllByEmail(emailLower);

        emailService.sendAccountCreated(user.getEmail(), user.getName());

        return buildAuthResponse(user);
    }

    /* ── LOGIN ─────────────────────────────────────────────────── */
    @Transactional
    public AuthResponse login(LoginRequest req) {
        User user = userRepo.findByEmail(req.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!encoder.matches(req.getPassword(), user.getPasswordHash()))
            throw new IllegalArgumentException("Invalid email or password");

        if (!user.isActive() || user.getDeletedAt() != null)
            throw new IllegalArgumentException("This account is no longer active");

        user.setLastLoginAt(LocalDateTime.now());
        userRepo.save(user);

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

        User user = rt.getUser();
        if (!user.isActive() || user.getDeletedAt() != null)
            throw new IllegalArgumentException("This account is no longer active");

        return buildAuthResponse(user);
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
            System.out.println("[TEMP-DEBUG] forgot-password otp for " + req.getEmail() + " = " + otp);
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
            throw new IllegalArgumentException("Code has expired");

        if (!token.getOtp().equals(req.getOtp()))
            throw new IllegalArgumentException("Invalid OTP");

        User user = userRepo.findByEmail(req.getEmail().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.setPasswordHash(encoder.encode(req.getNewPassword()));
        userRepo.save(user);

        otpRepo.invalidateAllByEmail(req.getEmail().toLowerCase());
        refreshRepo.revokeAllByUserId(user.getId());

        emailService.sendPasswordChanged(user.getEmail());
    }

    /* ── Also used by ProfileService: a profile update can change the email
       encoded as the JWT subject, so it needs to mint a fresh token pair
       reflecting the new identity rather than leaving the caller holding a
       token for an email that (after the update) no longer resolves to any
       user. ───────────────────────────────────────────────────────────── */
    public AuthResponse buildAuthResponse(User user) {
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
