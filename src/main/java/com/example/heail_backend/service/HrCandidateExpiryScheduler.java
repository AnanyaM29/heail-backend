package com.example.heail_backend.service;

import com.example.heail_backend.entity.HrCandidate;
import com.example.heail_backend.repository.HrCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Daily pass flipping unstarted candidates past their 7-day access window
 *  to EXPIRED and notifying the buyer — mirrors PulseReminderScheduler's
 *  shape (a small @Component, @Scheduled, one pass per run). */
@Slf4j
@Component
@RequiredArgsConstructor
public class HrCandidateExpiryScheduler {

    private final HrCandidateRepository hrCandidateRepo;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void expireLapsedCandidates() {
        List<HrCandidate> lapsed = hrCandidateRepo
                .findByStatusInAndTokenExpiresAtBefore(List.of("SENT", "ACCESSED"), LocalDateTime.now());

        for (HrCandidate candidate : lapsed) {
            candidate.setStatus("EXPIRED");
            hrCandidateRepo.save(candidate);
            try {
                emailService.sendBuyerCandidateExpired(candidate.getOrder().getUser().getEmail(),
                        candidate.getOrder().getUser().getName(), candidate.getName());
            } catch (Exception e) {
                log.error("Failed to send candidate-expired notification for {}: {}", candidate.getId(), e.getMessage());
            }
        }
    }
}
