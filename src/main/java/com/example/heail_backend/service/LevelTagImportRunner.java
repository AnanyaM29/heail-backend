package com.example.heail_backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * No-ops on every normal startup. Set IMPORT_LEVEL_TAGS_PATH to a folder of
 * HEAIL_S##_QuestionBank_100.xlsx workbooks to run the Phase 1 level-tag
 * ingest once, then unset it — this is a one-off migration trigger, not a
 * standing admin endpoint, so there's nothing exposed over HTTP that can
 * mutate question_bank content.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LevelTagImportRunner implements ApplicationRunner {

    private final LevelTagImportService importService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String path = System.getenv("IMPORT_LEVEL_TAGS_PATH");
        if (path == null || path.isBlank()) return;

        log.info("IMPORT_LEVEL_TAGS_PATH is set — running level-tag import from: {}", path);
        LevelTagImportService.ImportResult result = importService.importFromWorkbooks(Path.of(path));

        if (result.aborted()) {
            log.error("IMPORT ABORTED — {} text mismatch(es) found, zero rows updated. Fix the mismatches above and re-run.", result.mismatches().size());
            return;
        }

        log.info("IMPORT COMPLETE — {} rows checked, {} rows updated.", result.rowsChecked(), result.rowsUpdated());
        for (var dist : result.distributions()) {
            log.info("  {} — before: {} | after: {}", dist.sectionCode(), dist.before(), dist.after());
        }
    }
}
