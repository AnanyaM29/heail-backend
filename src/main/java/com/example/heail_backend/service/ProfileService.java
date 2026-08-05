package com.example.heail_backend.service;

import com.example.heail_backend.dto.AuthResponse;
import com.example.heail_backend.dto.ProfileOtpRequest;
import com.example.heail_backend.dto.ProfileResponse;
import com.example.heail_backend.dto.UpdateProfileRequest;
import com.example.heail_backend.dto.VerifyProfileOtpRequest;
import com.example.heail_backend.entity.OtpToken;
import com.example.heail_backend.entity.User;
import com.example.heail_backend.repository.OtpTokenRepository;
import com.example.heail_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * Lets a logged-in person view and edit their own profile (name, city,
 * country, email, mobile). Two things are deliberately gated by OTP:
 *
 *  1. Editing ANYTHING requires first proving the caller still controls the
 *     account's CURRENT email (purpose "CURRENT") — this is the "unlock"
 *     step; nothing can be changed until it's verified.
 *  2. Changing the email or mobile to a NEW value additionally requires its
 *     own separate code — "NEW_EMAIL" is emailed to the prospective new
 *     address itself (and that address must not already belong to another
 *     account); "NEW_MOBILE" is emailed to the account's current address,
 *     since there's no SMS gateway wired up in this project yet.
 *
 * Verification happens via /send-otp + /verify-otp, each of which marks the
 * matching OtpToken row "used". The final save (updateProfile) doesn't
 * re-accept raw OTP codes — it just checks that a recently-verified (used,
 * not stale) token of the right purpose already exists for the value being
 * saved, which is what /verify-otp produced a moment earlier in the same
 * session.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int RECENCY_WINDOW_MINUTES = 30;

    private static final String PURPOSE_CURRENT     = "PROFILE_CURRENT";
    private static final String PURPOSE_NEW_EMAIL   = "PROFILE_NEW_EMAIL";
    private static final String PURPOSE_NEW_MOBILE  = "PROFILE_NEW_MOBILE";

    private final UserRepository userRepo;
    private final OtpTokenRepository otpRepo;
    private final EmailService emailService;
    private final AuthService authService;
    private final Random random = new Random();

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String accountEmail) {
        return toResponse(requireUser(accountEmail));
    }

    /* ── Send a purpose-scoped OTP ─────────────────────────────── */
    @Transactional
    public void sendOtp(String accountEmail, ProfileOtpRequest req) {
        User user = requireUser(accountEmail);
        String purpose = req.getPurpose() == null ? "" : req.getPurpose().trim().toUpperCase();

        switch (purpose) {
            case "CURRENT" ->
                issueOtp(user.getEmail(), PURPOSE_CURRENT, "confirm it's you");
            case "NEW_MOBILE" ->
                issueOtp(user.getEmail(), PURPOSE_NEW_MOBILE, "confirm your new mobile number");
            case "NEW_EMAIL" -> {
                String newEmail = req.getTarget() == null ? "" : req.getTarget().trim().toLowerCase();
                if (!EMAIL_PATTERN.matcher(newEmail).matches())
                    throw new IllegalArgumentException("A valid email is required");
                if (!newEmail.equalsIgnoreCase(user.getEmail()) && userRepo.existsByEmail(newEmail))
                    throw new IllegalArgumentException("That email is already registered to another account");
                issueOtp(newEmail, PURPOSE_NEW_EMAIL, "confirm your new email address");
            }
            default -> throw new IllegalArgumentException("Unknown OTP purpose: " + req.getPurpose());
        }
    }

    /* ── Verify (and consume) a purpose-scoped OTP ─────────────── */
    @Transactional
    public void verifyOtp(String accountEmail, VerifyProfileOtpRequest req) {
        User user = requireUser(accountEmail);
        String purpose = req.getPurpose() == null ? "" : req.getPurpose().trim().toUpperCase();

        String keyEmail = switch (purpose) {
            case "CURRENT" -> user.getEmail();
            case "NEW_MOBILE" -> user.getEmail();
            case "NEW_EMAIL" -> {
                String t = req.getTarget() == null ? "" : req.getTarget().trim().toLowerCase();
                if (t.isBlank()) throw new IllegalArgumentException("Missing target email");
                yield t;
            }
            default -> throw new IllegalArgumentException("Unknown OTP purpose: " + req.getPurpose());
        };

        String mappedPurpose = switch (purpose) {
            case "CURRENT" -> PURPOSE_CURRENT;
            case "NEW_MOBILE" -> PURPOSE_NEW_MOBILE;
            case "NEW_EMAIL" -> PURPOSE_NEW_EMAIL;
            default -> throw new IllegalArgumentException("Unknown OTP purpose: " + req.getPurpose());
        };

        consumeOtp(keyEmail, mappedPurpose, req.getOtp());
    }

    /* ── Save changes, re-checking that the required OTPs were verified ──
       Returns a fresh AuthResponse (not just ProfileResponse) because an
       email change moves the JWT subject — the caller's existing access
       token would otherwise stop resolving to any user on the very next
       request. The frontend re-persists this exactly like it does after
       login/register. */
    @Transactional
    public AuthResponse updateProfile(String accountEmail, UpdateProfileRequest req) {
        User user = requireUser(accountEmail);

        requireRecentlyVerified(user.getEmail(), PURPOSE_CURRENT,
                "Please verify the code sent to your current email before saving changes");

        String newEmail = req.getEmail().trim().toLowerCase();
        boolean emailChanged = !newEmail.equalsIgnoreCase(user.getEmail());
        if (emailChanged) {
            if (!EMAIL_PATTERN.matcher(newEmail).matches())
                throw new IllegalArgumentException("A valid email is required");
            if (userRepo.existsByEmail(newEmail))
                throw new IllegalArgumentException("That email is already registered to another account");
            requireRecentlyVerified(newEmail, PURPOSE_NEW_EMAIL,
                    "Please verify the code sent to your new email address before saving changes");
        }

        String newMobile = req.getMobile() == null ? "" : req.getMobile().trim();
        String oldMobile = user.getMobile() == null ? "" : user.getMobile();
        boolean mobileChanged = !newMobile.equals(oldMobile);
        if (mobileChanged && !newMobile.isEmpty()) {
            requireRecentlyVerified(user.getEmail(), PURPOSE_NEW_MOBILE,
                    "Please verify the code confirming your new mobile number before saving changes");
        }

        user.setName(req.getName().trim());
        user.setCity(req.getCity() == null || req.getCity().isBlank() ? null : req.getCity().trim());
        user.setCountry(req.getCountry() == null || req.getCountry().isBlank() ? null : req.getCountry().trim());
        if (emailChanged) user.setEmail(newEmail);
        user.setMobile(newMobile.isEmpty() ? null : newMobile);
        user = userRepo.save(user);

        emailService.sendProfileUpdated(user.getEmail());
        return authService.buildAuthResponse(user);
    }

    /* ── Private helpers ───────────────────────────────────────── */
    private void issueOtp(String email, String purpose, String reason) {
        otpRepo.invalidateAllByEmailAndPurpose(email, purpose);

        String otp = String.format("%06d", random.nextInt(1_000_000));
        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setPurpose(purpose);
        token.setOtp(otp);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        token.setUsed(false);
        otpRepo.save(token);

        emailService.sendProfileOtp(email, otp, reason);
    }

    private void consumeOtp(String email, String purpose, String submitted) {
        OtpToken token = otpRepo.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new IllegalArgumentException("No verification code was requested for " + email));

        if (token.isUsed())
            throw new IllegalArgumentException("This code has already been used — request a new one");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Code has expired — request a new one");
        if (!token.getOtp().equals(submitted))
            throw new IllegalArgumentException("Incorrect code");

        token.setUsed(true);
        otpRepo.save(token);
    }

    /** Save-time re-check: was the latest token for this (email, purpose) pair
     *  actually verified (used=true), and recently enough to still trust? */
    private void requireRecentlyVerified(String email, String purpose, String message) {
        OtpToken token = otpRepo.findTopByEmailAndPurposeOrderByCreatedAtDesc(email, purpose)
                .orElseThrow(() -> new IllegalArgumentException(message));
        if (!token.isUsed() || token.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(RECENCY_WINDOW_MINUTES)))
            throw new IllegalArgumentException(message);
    }

    private User requireUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private ProfileResponse toResponse(User user) {
        ProfileResponse res = new ProfileResponse();
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setMobile(user.getMobile());
        res.setCity(user.getCity());
        res.setCountry(user.getCountry());
        res.setRole(user.getRole());
        return res;
    }
}
