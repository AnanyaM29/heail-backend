package com.example.heail_backend.service;

import com.example.heail_backend.entity.QuestionBank;
import com.example.heail_backend.repository.QuestionBankRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LevelTagImportServiceTest {

    private static final String QUESTION_ID = "S01C01Q01";
    private static final String QUESTION_TEXT = "The organisation's sales strategy is:";

    private final QuestionBankRepository repo = mock(QuestionBankRepository.class);
    private final LevelTagImportService service = new LevelTagImportService(repo);

    @Test
    void idempotentReRunProducesNoChanges(@TempDir Path tempDir) throws Exception {
        writeWorkbook(tempDir, "HEAIL_S01_QuestionBank_100.xlsx", QUESTION_ID, "L+MM+E", QUESTION_TEXT);

        QuestionBank existing = questionRow("L+MM+E", QUESTION_TEXT); // already matches the workbook
        when(repo.findByQuestionIdIn(anyList())).thenReturn(List.of(existing));

        LevelTagImportService.ImportResult result = service.importFromWorkbooks(tempDir);

        assertThat(result.aborted()).isFalse();
        assertThat(result.mismatches()).isEmpty();
        assertThat(result.rowsChecked()).isEqualTo(1);
        assertThat(result.rowsUpdated()).isEqualTo(0);
        verify(repo, never()).save(any());

        // Re-running against the same (unchanged) state must still be a no-op.
        LevelTagImportService.ImportResult secondRun = service.importFromWorkbooks(tempDir);
        assertThat(secondRun.rowsUpdated()).isEqualTo(0);
        assertThat(secondRun.aborted()).isFalse();
    }

    @Test
    void levelChangeIsAppliedWhenTextMatches(@TempDir Path tempDir) throws Exception {
        writeWorkbook(tempDir, "HEAIL_S01_QuestionBank_100.xlsx", QUESTION_ID, "L+MM+E", QUESTION_TEXT);

        QuestionBank existing = questionRow("L+MM", QUESTION_TEXT); // text matches, level is stale
        when(repo.findByQuestionIdIn(anyList())).thenReturn(List.of(existing));

        LevelTagImportService.ImportResult result = service.importFromWorkbooks(tempDir);

        assertThat(result.aborted()).isFalse();
        assertThat(result.rowsUpdated()).isEqualTo(1);
        assertThat(existing.getLevelTag()).isEqualTo("L+MM+E");
        verify(repo).save(existing);
    }

    @Test
    void textMismatchAbortsWithZeroWrites(@TempDir Path tempDir) throws Exception {
        writeWorkbook(tempDir, "HEAIL_S01_QuestionBank_100.xlsx", QUESTION_ID, "L+MM+E", QUESTION_TEXT);

        QuestionBank existing = questionRow("L+MM", "This text has drifted from the workbook");
        when(repo.findByQuestionIdIn(anyList())).thenReturn(List.of(existing));

        LevelTagImportService.ImportResult result = service.importFromWorkbooks(tempDir);

        assertThat(result.aborted()).isTrue();
        assertThat(result.mismatches()).hasSize(1);
        assertThat(result.mismatches().get(0).questionId()).isEqualTo(QUESTION_ID);
        assertThat(result.rowsUpdated()).isEqualTo(0);
        assertThat(existing.getLevelTag()).isEqualTo("L+MM"); // untouched
        verify(repo, never()).save(any());
    }

    private QuestionBank questionRow(String levelTag, String text) {
        QuestionBank q = new QuestionBank();
        q.setQuestionId(QUESTION_ID);
        q.setSectionCode("S01");
        q.setCategoryCode("C01");
        q.setLevelTag(levelTag);
        q.setText(text);
        q.setOptionA("A"); q.setOptionB("B"); q.setOptionC("C"); q.setOptionD("D");
        q.setScoreA((short) 5); q.setScoreB((short) 3); q.setScoreC((short) 2); q.setScoreD((short) 1);
        q.setActive(true);
        return q;
    }

    private void writeWorkbook(Path dir, String filename, String questionId, String level, String text) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Question Bank");
            sheet.createRow(0); // title row
            sheet.createRow(1); // legend row
            sheet.createRow(2); // column-header row
            Row dataRow = sheet.createRow(3);
            dataRow.createCell(2).setCellValue(questionId); // column C
            dataRow.createCell(3).setCellValue(level);       // column D
            dataRow.createCell(4).setCellValue(text);        // column E
            try (FileOutputStream fos = new FileOutputStream(dir.resolve(filename).toFile())) {
                wb.write(fos);
            }
        }
    }
}
