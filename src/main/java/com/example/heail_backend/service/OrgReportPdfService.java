package com.example.heail_backend.service;

import com.example.heail_backend.dto.OrgReportResponse;
import com.example.heail_backend.dto.PulseRagDto;
import com.example.heail_backend.dto.QuestionFindingDto;
import com.example.heail_backend.dto.SectionRagDto;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Renders the org diagnostic report (see OrgReportResponse/ScoringService) as a PDF,
 *  attached to the "report released" email — same OpenPDF approach as InvoiceService. */
@Service
public class OrgReportPdfService {

    private static final Color NAVY = new Color(19, 41, 75);

    public byte[] generate(OrgReportResponse report) {
        try {
            Document document = new Document(PageSize.A4, 45, 45, 50, 50);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, NAVY);
            Font h2Font = new Font(Font.HELVETICA, 14, Font.BOLD, NAVY);
            Font headingFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, 9.5f, Font.NORMAL);
            Font mutedFont = new Font(Font.HELVETICA, 8.5f, Font.NORMAL, Color.GRAY);
            Font bigFont = new Font(Font.HELVETICA, 30, Font.BOLD, NAVY);

            document.add(new Paragraph("HEAIL", titleFont));
            document.add(new Paragraph("Organisational Transformation Diagnostic — Report", mutedFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph(report.getOrganisationName() != null ? report.getOrganisationName() : "Your Organisation", h2Font));
            document.add(new Paragraph("Released " + (report.getReleasedAt() != null
                    ? report.getReleasedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "—")
                    + " · " + report.getRespondentCount() + " respondents fully completed all four Pulses", mutedFont));
            document.add(Chunk.NEWLINE);

            // ── Overall result ──────────────────────────────────────
            document.add(new Paragraph("Overall Result", h2Font));
            document.add(new Paragraph(String.valueOf(Math.round(report.getOverallIndex())), bigFont));
            document.add(new Paragraph(bandLabel(report.getOverallBand()) + "  (index 0–100)", bodyFont));
            document.add(new Paragraph(report.getDivergenceIndex() != null
                    ? "Divergence index: " + fmt(report.getDivergenceIndex()) + " — how much leadership and delivery staff disagree with each other, on average."
                    : "Divergence index not computable yet — no section currently clears the 5-respondents-per-level floor.", bodyFont));
            document.add(Chunk.NEWLINE);

            // ── Four pulses ──────────────────────────────────────────
            document.add(new Paragraph("Four Pulses", h2Font));
            PdfPTable pulseTable = new PdfPTable(6);
            pulseTable.setWidthPercentage(100);
            pulseTable.setWidths(new float[]{2.2f, 1, 1.4f, 1, 1.4f, 1.2f});
            addHeaderRow(pulseTable, "Pulse", "Index", "Band", "Net Gap", "Divergence", "Consensus");
            for (PulseRagDto p : report.getPulses()) {
                addCell(pulseTable, p.getDisplayName(), bodyFont);
                addCell(pulseTable, String.valueOf(Math.round(p.getIndex())), bodyFont);
                addCell(pulseTable, bandLabel(p.getBand()), bodyFont);
                addCell(pulseTable, p.getNetGap() != null ? signed(p.getNetGap()) : "—", bodyFont);
                addCell(pulseTable, fmtOrDash(p.getDivergenceIndex()), bodyFont);
                addCell(pulseTable, fmtOrDash(p.getConsensus()), bodyFont);
            }
            document.add(pulseTable);
            document.add(Chunk.NEWLINE);

            // ── Perception spine (all sections, sorted by |gap| desc) ─
            document.add(new Paragraph("The Perception Spine", h2Font));
            document.add(new Paragraph("Sorted by gap — where leadership is most wrong first.", mutedFont));
            PdfPTable sectionTable = new PdfPTable(5);
            sectionTable.setWidthPercentage(100);
            sectionTable.setWidths(new float[]{2.6f, 0.8f, 0.8f, 0.8f, 0.8f});
            addHeaderRow(sectionTable, "Section", "Leadership", "Mid-Mgmt", "Employees", "Gap");
            for (SectionRagDto s : report.getSections()) {
                addCell(sectionTable, s.getSectionName(), bodyFont);
                addCell(sectionTable, fmtOrDash(s.getLevelL()), bodyFont);
                addCell(sectionTable, fmtOrDash(s.getLevelMM()), bodyFont);
                addCell(sectionTable, fmtOrDash(s.getLevelE()), bodyFont);
                addCell(sectionTable, s.getGap() != null ? signed(s.getGap()) : "—", bodyFont);
            }
            document.add(sectionTable);
            document.add(Chunk.NEWLINE);

            // ── Top findings ───────────────────────────────────────
            List<QuestionFindingDto> findings = report.getFindings();
            if (findings != null && !findings.isEmpty()) {
                document.add(new Paragraph("Questions Worth the Whole Report", h2Font));
                for (QuestionFindingDto f : findings) {
                    document.add(new Paragraph("\"" + f.getText() + "\"", headingFont));
                    document.add(new Paragraph(
                            f.getSectionName() + " — Leadership " + fmtOrDash(f.getLevelL())
                                    + " / Mid-Mgmt " + fmtOrDash(f.getLevelMM())
                                    + " / Employees " + fmtOrDash(f.getLevelE())
                                    + " / Gap " + signed(f.getGap()), mutedFont));
                    document.add(Chunk.NEWLINE);
                }
            }

            // ── Data quality ─────────────────────────────────────────
            document.add(new Paragraph("How Much of This Can You Trust", h2Font));
            PdfPTable dqTable = new PdfPTable(2);
            dqTable.setWidthPercentage(100);
            dqTable.setWidths(new float[]{2, 3});
            addDqRow(dqTable, "Level coverage (L / MM / E)",
                    report.getDataQuality().getRespondentsL() + " / " + report.getDataQuality().getRespondentsMM() + " / " + report.getDataQuality().getRespondentsE(), headingFont, bodyFont);
            addDqRow(dqTable, "Shared questions", String.valueOf(report.getDataQuality().getSharedQuestions()), headingFont, bodyFont);
            addDqRow(dqTable, "Straight-lining", String.valueOf(report.getDataQuality().getStraightliningCount()), headingFont, bodyFont);
            addDqRow(dqTable, "Silence rate", fmt(report.getDataQuality().getSilenceRatePct()) + "%", headingFont, bodyFont);
            addDqRow(dqTable, "Voice confidence", fmt(report.getDataQuality().getVoiceConfidence()), headingFont, bodyFont);
            if (report.getDataQuality().getMedianCompletionMinutes() != null) {
                addDqRow(dqTable, "Median completion", report.getDataQuality().getMedianCompletionMinutes() + " min", headingFont, bodyFont);
            }
            document.add(dqTable);
            document.add(Chunk.NEWLINE);

            // ── Risk indices ─────────────────────────────────────────
            if (report.getRisk() != null && (report.getRisk().getTrustDeficit() != null
                    || report.getRisk().getAttritionSignal() != null
                    || report.getRisk().getChangeFatigue() != null
                    || report.getRisk().getExecutionDrag() != null)) {
                document.add(new Paragraph("What This Costs", h2Font));
                PdfPTable riskTable = new PdfPTable(2);
                riskTable.setWidthPercentage(100);
                riskTable.setWidths(new float[]{2, 3});
                if (report.getRisk().getTrustDeficit() != null)
                    addDqRow(riskTable, "Trust deficit", fmt(report.getRisk().getTrustDeficit()), headingFont, bodyFont);
                if (report.getRisk().getExecutionDrag() != null)
                    addDqRow(riskTable, "Execution drag", signed(report.getRisk().getExecutionDrag()), headingFont, bodyFont);
                if (report.getRisk().getAttritionSignal() != null)
                    addDqRow(riskTable, "Attrition signal", fmt(report.getRisk().getAttritionSignal()), headingFont, bodyFont);
                if (report.getRisk().getChangeFatigue() != null)
                    addDqRow(riskTable, "Change fatigue", fmt(report.getRisk().getChangeFatigue()), headingFont, bodyFont);
                document.add(riskTable);
                document.add(Chunk.NEWLINE);
            }

            document.add(new Paragraph("This report is generated automatically by HEAIL and reflects responses "
                    + "collected up to the release date above. Individual employee answers remain confidential — "
                    + "only aggregate, level-based results are ever shown.", mutedFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate org report PDF", e);
        }
    }

    private String bandLabel(String band) {
        if (band == null) return "—";
        return switch (band) {
            case "emerging" -> "Emerging";
            case "developing" -> "Developing";
            case "established" -> "Established";
            case "leading" -> "Leading";
            default -> band;
        };
    }

    private String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private String fmtOrDash(Double v) {
        return v != null ? fmt(v) : "—";
    }

    private String signed(double v) {
        return (v > 0 ? "+" : "") + fmt(v);
    }

    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, new Font(Font.HELVETICA, 9, Font.BOLD, Color.WHITE)));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addDqRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPadding(5);
        labelCell.setBorderColor(new Color(230, 220, 196));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setPadding(5);
        valueCell.setBorderColor(new Color(230, 220, 196));
        table.addCell(valueCell);
    }
}
