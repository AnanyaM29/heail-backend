package com.example.heail_backend.service;

import com.example.heail_backend.entity.QuestionBank;
import com.example.heail_backend.repository.QuestionBankRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ingests Column D (level tag) changes from the HEAIL_QuestionBank_E_rebalanced
 * workbooks. Only ever touches level_tag — text, options, scores and category
 * assignment are read for verification only, never written.
 *
 * Validate-then-apply, not row-by-row: every row from every workbook is checked
 * against the DB first; if ANY question's text has drifted from what the
 * workbook says, the whole run aborts with zero writes (see ImportResult#aborted)
 * — this is what makes a partial/stale rebalance impossible to apply by accident.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LevelTagImportService {

    private static final Pattern SECTION_FROM_FILENAME = Pattern.compile("HEAIL_(S\\d{2})_");
    private static final int COL_QUESTION_ID = 2; // column C
    private static final int COL_LEVEL       = 3; // column D
    private static final int COL_TEXT        = 4; // column E
    private static final int HEADER_ROWS     = 3; // title, legend, column-header rows

    private final QuestionBankRepository questionBankRepo;

    public record RowMismatch(String questionId, String dbText, String workbookText) {}

    public record SectionDistribution(String sectionCode, Map<String, Integer> before, Map<String, Integer> after) {}

    public record ImportResult(
            boolean aborted,
            List<RowMismatch> mismatches,
            int rowsChecked,
            int rowsUpdated,
            List<SectionDistribution> distributions
    ) {}

    private record ParsedRow(String sectionCode, String questionId, String levelTag, String text) {}

    @Transactional
    public ImportResult importFromWorkbooks(Path folder) throws IOException {
        List<ParsedRow> rows = parseWorkbooks(folder);

        // One batched lookup instead of one round-trip per row — 2000 individual
        // findById() calls against a pooled cloud Postgres instance is minutes,
        // this is a couple of seconds.
        List<String> allIds = rows.stream().map(ParsedRow::questionId).distinct().toList();
        Map<String, QuestionBank> dbByQuestionId = questionBankRepo.findByQuestionIdIn(allIds).stream()
                .collect(java.util.stream.Collectors.toMap(QuestionBank::getQuestionId, q -> q));

        List<RowMismatch> mismatches = new ArrayList<>();
        Map<String, QuestionBank> verified = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> before = new LinkedHashMap<>();

        for (ParsedRow row : rows) {
            QuestionBank existing = dbByQuestionId.get(row.questionId());
            if (existing == null) {
                mismatches.add(new RowMismatch(row.questionId(), "<no matching row in question_bank>", row.text()));
                continue;
            }
            if (!normalize(existing.getText()).equals(normalize(row.text()))) {
                mismatches.add(new RowMismatch(row.questionId(), existing.getText(), row.text()));
                continue;
            }
            verified.put(row.questionId(), existing);
            before.computeIfAbsent(row.sectionCode(), k -> new TreeMap<>())
                    .merge(existing.getLevelTag(), 1, Integer::sum);
        }

        if (!mismatches.isEmpty()) {
            log.error("Level-tag import ABORTED — {} question(s) have text that no longer matches the workbook. Zero rows updated.", mismatches.size());
            mismatches.forEach(m -> log.error("  {} — DB text: \"{}\" | workbook text: \"{}\"", m.questionId(), m.dbText(), m.workbookText()));
            return new ImportResult(true, mismatches, rows.size(), 0, List.of());
        }

        int updated = 0;
        Map<String, Map<String, Integer>> after = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            QuestionBank existing = verified.get(row.questionId());
            if (!existing.getLevelTag().equals(row.levelTag())) {
                existing.setLevelTag(row.levelTag());
                questionBankRepo.save(existing);
                updated++;
            }
            after.computeIfAbsent(row.sectionCode(), k -> new TreeMap<>())
                    .merge(row.levelTag(), 1, Integer::sum);
        }

        List<SectionDistribution> distributions = new ArrayList<>();
        for (String sectionCode : before.keySet()) {
            distributions.add(new SectionDistribution(sectionCode, before.get(sectionCode), after.get(sectionCode)));
        }

        log.info("Level-tag import complete — {} rows checked, {} updated, 0 mismatches.", rows.size(), updated);
        return new ImportResult(false, List.of(), rows.size(), updated, distributions);
    }

    private List<ParsedRow> parseWorkbooks(Path folder) throws IOException {
        List<Path> files;
        try (var stream = Files.list(folder)) {
            files = stream
                    .filter(p -> p.getFileName().toString().matches("HEAIL_S\\d{2}_QuestionBank_100\\.xlsx"))
                    .sorted()
                    .toList();
        }
        if (files.isEmpty())
            throw new IllegalArgumentException("No HEAIL_S##_QuestionBank_100.xlsx files found in " + folder);

        List<ParsedRow> rows = new ArrayList<>();
        for (Path file : files) {
            String sectionCode = extractSectionCode(file.getFileName().toString());
            try (FileInputStream fis = new FileInputStream(file.toFile());
                 XSSFWorkbook wb = new XSSFWorkbook(fis)) {

                Sheet sheet = wb.getSheetAt(0);
                for (Row row : sheet) {
                    if (row.getRowNum() < HEADER_ROWS) continue;
                    String questionId = cellText(row, COL_QUESTION_ID);
                    if (questionId == null || questionId.isBlank()) continue; // category divider row

                    String levelTag = cellText(row, COL_LEVEL);
                    String text = cellText(row, COL_TEXT);
                    rows.add(new ParsedRow(sectionCode, questionId, levelTag, text));
                }
            }
        }
        return rows;
    }

    private String extractSectionCode(String filename) {
        Matcher m = SECTION_FROM_FILENAME.matcher(filename);
        if (!m.find()) throw new IllegalArgumentException("Cannot extract section code from filename: " + filename);
        return m.group(1);
    }

    private String cellText(Row row, int colIndex) {
        Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BLANK -> null;
            default -> cell.toString().trim();
        };
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
