package com.example.heail_backend.service;

import com.example.heail_backend.entity.OtpToken;
import com.example.heail_backend.entity.PartnerApplication;
import com.example.heail_backend.repository.OtpTokenRepository;
import com.example.heail_backend.repository.PartnerApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PartnerService {

    private static final String PURPOSE = "PARTNER_APPLICATION";

    private final OtpTokenRepository otpRepo;
    private final PartnerApplicationRepository partnerRepo;
    private final EmailService emailService;
    private final SmsService smsService;

    @Transactional
    public void sendOtp(String email, String mobile) {
        String emailLower = email.toLowerCase();
        otpRepo.invalidateAllByEmailAndPurpose(emailLower, PURPOSE);

        String otp = String.format("%06d", new Random().nextInt(1_000_000));

        OtpToken token = new OtpToken();
        token.setEmail(emailLower);
        token.setOtp(otp);
        token.setPurpose(PURPOSE);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        token.setUsed(false);
        otpRepo.save(token);

        emailService.sendPartnerOtp(email, otp);
        smsService.sendOtp(mobile, otp);
    }

    /**
     * Minimal implementation for now — validates the email OTP and persists
     * whatever was submitted. No admin review workflow, no status field, no
     * outbound confirmation email yet; just captures applications so they
     * aren't lost while the rest of the partner pipeline gets built out.
     */
    @Transactional
    public void apply(String name, String country, String city, String mobile, String email,
                       String otp, boolean consentGiven, MultipartFile resume) {
        String emailLower = email.toLowerCase();
        OtpToken token = otpRepo.findTopByEmailAndPurposeOrderByCreatedAtDesc(emailLower, PURPOSE)
                .orElseThrow(() -> new IllegalArgumentException("Please request a verification code first"));

        if (token.isUsed())
            throw new IllegalArgumentException("This code has already been used — request a new one");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Code has expired");
        if (!token.getOtp().equals(otp))
            throw new IllegalArgumentException("Incorrect code");

        if (!consentGiven)
            throw new IllegalArgumentException("Consent is required to submit an application");

        PartnerApplication application = new PartnerApplication();
        application.setName(name);
        application.setCountry(country);
        application.setCity(city);
        application.setMobile(mobile);
        application.setEmail(emailLower);
        application.setConsentGiven(true);

        if (resume != null && !resume.isEmpty()) {
            application.setResumeFileName(resume.getOriginalFilename());
            try {
                application.setResumeData(resume.getBytes());
            } catch (IOException e) {
                throw new IllegalArgumentException("Could not read the uploaded resume file");
            }
        }

        partnerRepo.save(application);
        otpRepo.invalidateAllByEmailAndPurpose(emailLower, PURPOSE);
    }
}
