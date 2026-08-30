package com.example.heail_backend.service;

import com.example.heail_backend.entity.Order;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/**
 * Generates GST invoice PDFs for paid orders, and owns the shared invoice-number
 * counter (Postgres sequence {@code invoice_seq}) that both website-generated and
 * manual/offline invoices draw from, so numbers never collide.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final String HEAIL_GSTIN = "08AGZPM5226F1ZY";

    /** Change this (and redeploy) to switch the invoice-number format, e.g. at a financial-year rollover. */
    private static final String INVOICE_PREFIX = "HEAIL-INV-";
    private static final int INVOICE_PAD = 6;

    private final EntityManager entityManager;

    private String format(long n) {
        return INVOICE_PREFIX + String.format("%0" + INVOICE_PAD + "d", n);
    }

    private void ensureSequence() {
        entityManager.createNativeQuery("CREATE SEQUENCE IF NOT EXISTS invoice_seq").executeUpdate();
    }

    /**
     * Allocates the next invoice number and advances the shared counter. Called by the
     * website on payment and by the superadmin endpoint when cutting a manual invoice —
     * one sequence, so a manual and an automatic invoice can never share a number.
     */
    @Transactional
    public String nextInvoiceNumber() {
        ensureSequence();
        Number next = (Number) entityManager.createNativeQuery("SELECT nextval('invoice_seq')").getSingleResult();
        return format(next.longValue());
    }

    /** Current counter state for the superadmin screen — does not advance it. */
    @Transactional
    public Counter peekCounter() {
        ensureSequence();
        Object[] row = (Object[]) entityManager
                .createNativeQuery("SELECT last_value, is_called FROM invoice_seq").getSingleResult();
        long lastValue = ((Number) row[0]).longValue();
        boolean isCalled = (Boolean) row[1];
        long nextValue = isCalled ? lastValue + 1 : lastValue;
        return new Counter(isCalled ? lastValue : null, nextValue, format(nextValue));
    }

    /**
     * Manually sets the counter so the next invoice number is {@code nextValue}
     * (e.g. after issuing a batch of manual invoices, or a financial-year reset).
     */
    @Transactional
    public Counter setCounter(long nextValue) {
        if (nextValue < 1) throw new IllegalArgumentException("nextValue must be >= 1");
        ensureSequence();
        // nextValue is a validated long — safe to inline; ALTER SEQUENCE takes no bind params.
        entityManager.createNativeQuery("ALTER SEQUENCE invoice_seq RESTART WITH " + nextValue).executeUpdate();
        return peekCounter();
    }

    /** @param lastUsed null if no number has been issued yet. */
    public record Counter(Long lastUsed, long nextValue, String nextNumber) {}

    public byte[] generate(Order order, String customerName, String customerEmail) {
        try {
            Document document = new Document(PageSize.A4, 50, 50, 50, 50);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(19, 41, 75));
            Font headingFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            Font bodyFont = new Font(Font.HELVETICA, 10, Font.NORMAL);
            Font mutedFont = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);

            Paragraph title = new Paragraph("HEAIL", titleFont);
            document.add(title);
            document.add(new Paragraph("Human Experience + AI Logic", mutedFont));
            document.add(new Paragraph("contact@heail.in", mutedFont));
            document.add(new Paragraph("GSTIN: " + HEAIL_GSTIN, mutedFont));
            document.add(Chunk.NEWLINE);

            Paragraph invoiceHeading = new Paragraph("TAX INVOICE", headingFont);
            document.add(invoiceHeading);
            document.add(new Paragraph("Invoice Number: " + order.getInvoiceNumber(), bodyFont));
            document.add(new Paragraph("Invoice Date: " +
                    order.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")), bodyFont));
            document.add(new Paragraph("Payment Reference: " + order.getGatewayOrderRef(), bodyFont));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Billed To:", headingFont));
            document.add(new Paragraph(customerName, bodyFont));
            document.add(new Paragraph(customerEmail, bodyFont));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3, 1});

            addHeaderCell(table, "Description");
            addHeaderCell(table, "Amount (" + order.getCurrency() + ")");

            addCell(table, productDisplayName(order.getProductCode()), bodyFont);
            addCell(table, order.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), bodyFont);

            addCell(table, "GST", bodyFont);
            addCell(table, order.getGstAmount().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), bodyFont);

            BigDecimal total = order.getAmount().add(order.getGstAmount());
            addCell(table, "Total", headingFont);
            addCell(table, total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString(), headingFont);

            document.add(table);
            document.add(Chunk.NEWLINE);
            document.add(new Paragraph("This is a system-generated invoice and does not require a signature.", mutedFont));

            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
    }

    private String productDisplayName(String productCode) {
        return switch (productCode) {
            case "LEADER_CLASSIC" -> "The Gita Leader — Classic Assessment";
            case "SUITE_4PULSE" -> "Organisational Transformation Diagnostic — 4-Pulse Suite";
            case "HR_SUITE" -> "HR Competency Assessment";
            default -> productCode;
        };
    }

    private void addHeaderCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(new Color(19, 41, 75));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(6);
        table.addCell(cell);
    }
}
