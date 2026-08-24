package com.example.heail_backend.service;

import com.example.heail_backend.entity.ContactMessage;
import com.example.heail_backend.entity.OtpToken;
import com.example.heail_backend.repository.ContactMessageRepository;
import com.example.heail_backend.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * "Get in Touch" form on the Contact Us page — one shared OTP verifies both
 * the email and mobile fields at once (entered as a single code), same
 * shape as PartnerService.sendOtp/apply. No account required.
 */
@Service
@RequiredArgsConstructor
public class ContactService {

    private static final String PURPOSE = "CONTACT_US";
    private static final int MAX_MESSAGE_WORDS = 200;

    private final OtpTokenRepository otpRepo;
    private final ContactMessageRepository contactRepo;
    private final EmailService emailService;
    private final SmsService smsService;

    @Value("${spring.mail.username:contact@heail.in}")
    private String heailEmail;

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

        emailService.sendContactOtp(email, otp);
        smsService.sendOtp(mobile, otp);
    }

    @Transactional
    public void submit(String name, String mobile, String email, String city, String country,
                        String message, String otp) {
        String emailLower = email.toLowerCase();
        OtpToken token = otpRepo.findTopByEmailAndPurposeOrderByCreatedAtDesc(emailLower, PURPOSE)
                .orElseThrow(() -> new IllegalArgumentException("Please request a verification code first"));

        if (token.isUsed())
            throw new IllegalArgumentException("This code has already been used — request a new one");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Code has expired");
        if (!token.getOtp().equals(otp))
            throw new IllegalArgumentException("Incorrect code");
        if (message.trim().split("\\s+").length > MAX_MESSAGE_WORDS)
            throw new IllegalArgumentException("Message must be " + MAX_MESSAGE_WORDS + " words or fewer");

        ContactMessage contact = new ContactMessage();
        contact.setName(name);
        contact.setMobile(mobile);
        contact.setEmail(emailLower);
        contact.setCity(city);
        contact.setCountry(country);
        contact.setMessage(message);
        contactRepo.save(contact);

        otpRepo.invalidateAllByEmailAndPurpose(emailLower, PURPOSE);

        emailService.sendContactMessageToHeail(heailEmail, name, mobile, emailLower, city, country, message);
    }
}
