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

/** Generates a simple GST invoice PDF for a paid order. */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final EntityManager entityManager;

    /** Allocates the next sequential invoice number, formatted e.g. HEAIL-INV-000123.
     *  Self-healing: creates the backing sequence on first use if it doesn't exist yet,
     *  since it's a raw Postgres sequence Hibernate's ddl-auto=update never creates. */
    @Transactional
    public String nextInvoiceNumber() {
        entityManager.createNativeQuery("CREATE SEQUENCE IF NOT EXISTS invoice_seq").executeUpdate();
        Number next = (Number) entityManager.createNativeQuery("SELECT nextval('invoice_seq')").getSingleResult();
        return "HEAIL-INV-" + String.format("%06d", next.longValue());
    }

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
