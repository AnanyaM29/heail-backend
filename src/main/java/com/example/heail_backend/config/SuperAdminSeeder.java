package com.example.heail_backend.config;

import com.example.heail_backend.entity.User;
import com.example.heail_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates a SUPERADMIN account on startup from app.superadmin.email/password
 * (env-configurable, blank by default) — there's no self-registration path for
 * this role, so this is the only way to get the first admin account into the
 * database. Blank properties skip seeding entirely; an existing account with
 * that email is left untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;

    @Value("${app.superadmin.email:}")
    private String superadminEmail;

    @Value("${app.superadmin.password:}")
    private String superadminPassword;

    @Override
    public void run(String... args) {
        if (superadminEmail == null || superadminEmail.isBlank()
                || superadminPassword == null || superadminPassword.isBlank()) {
            return;
        }

        String email = superadminEmail.toLowerCase().trim();
        if (userRepo.existsByEmail(email)) return;

        User admin = new User();
        admin.setName("Super Admin");
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(superadminPassword));
        admin.setRole("SUPERADMIN");
        userRepo.save(admin);

        log.info("Bootstrapped SUPERADMIN account for {}", email);
    }
}
