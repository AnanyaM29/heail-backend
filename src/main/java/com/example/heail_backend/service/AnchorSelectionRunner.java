package com.example.heail_backend.service;

import com.example.heail_backend.entity.QuestionBank;
import com.example.heail_backend.repository.QuestionBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.IntStream;

/**
 * No-ops on every normal startup. Set SELECT_ANCHORS=true to (re-)curate the
 * 2 fixed anchor questions per section once — same question, every
 * respondent, every wave, always tagged L+MM+E. Idempotent: re-running with
 * unchanged level tags reselects the same questions.
 *
 * There's no real response data yet to pick anchors by observed variance
 * (the ideal criterion — see heail-index-architecture.md), so this picks by
 * face validity instead: prefer questions whose text reads as evaluative/
 * subjective (trust, fairness, clarity, confidence) over purely procedural
 * ones, spread across categories within the section where possible. This is
 * a placeholder selection — revisit once wave-1 real data exists and swap in
 * genuinely high-variance items.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnchorSelectionRunner implements ApplicationRunner {

    private static final List<String> SECTION_CODES = IntStream.rangeClosed(1, 20)
            .mapToObj(i -> String.format("S%02d", i)).toList();

    private static final int ANCHORS_PER_SECTION = 2;

    private static final List<String> DISCRIMINATING_KEYWORDS = List.of(
            "trust", "fair", "believe", "understand", "confidence", "effective",
            "quality", "clear", "communicat", "feel", "honest", "transparent", "respect");

    private final QuestionBankRepository questionBankRepo;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!"true".equalsIgnoreCase(System.getenv("SELECT_ANCHORS"))) return;

        for (String sectionCode : SECTION_CODES) {
            List<QuestionBank> candidates = questionBankRepo.findBySectionCodeAndActiveTrue(sectionCode).stream()
                    .filter(this::isLmme)
                    .sorted(Comparator.comparingInt(this::discriminatingScore).reversed()
                            .thenComparing(QuestionBank::getQuestionId))
                    .toList();

            List<QuestionBank> chosen = new ArrayList<>();
            Set<String> usedCategories = new HashSet<>();
            for (QuestionBank q : candidates) {
                if (chosen.size() >= ANCHORS_PER_SECTION) break;
                if (usedCategories.contains(q.getCategoryCode())) continue;
                chosen.add(q);
                usedCategories.add(q.getCategoryCode());
            }
            // If the category-diversity pass couldn't fill both slots (e.g. every eligible
            // question happens to sit in the same category), fill remaining slots regardless.
            if (chosen.size() < ANCHORS_PER_SECTION) {
                for (QuestionBank q : candidates) {
                    if (chosen.size() >= ANCHORS_PER_SECTION) break;
                    if (!chosen.contains(q)) chosen.add(q);
                }
            }

            for (QuestionBank q : chosen) {
                q.setAnchor(true);
                questionBankRepo.save(q);
            }

            String flag = chosen.size() < ANCHORS_PER_SECTION
                    ? " — SHORT (only " + candidates.size() + " L+MM+E question(s) available in this section)"
                    : "";
            log.info("SELECT_ANCHORS — {}: {} anchor(s) selected{}", sectionCode, chosen.size(), flag);
        }
    }

    private boolean isLmme(QuestionBank q) {
        Set<String> parts = new HashSet<>(Arrays.asList(q.getLevelTag().split("\\+")));
        return parts.containsAll(Set.of("L", "MM", "E"));
    }

    private int discriminatingScore(QuestionBank q) {
        String text = q.getText().toLowerCase();
        int score = 0;
        for (String kw : DISCRIMINATING_KEYWORDS) if (text.contains(kw)) score++;
        return score;
    }
}
