package com.example.heail_backend.controller;

import com.example.heail_backend.service.InvoiceService;
import com.example.heail_backend.service.InvoiceService.Counter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Superadmin control of the shared invoice-number counter (Postgres sequence
 * {@code invoice_seq}). Website invoices and manual/offline invoices both draw
 * from it, so numbers stay in one sequence with no duplicates.
 *
 * <ul>
 *   <li>GET  /api/v1/admin/invoice-number        — view the counter and the next number</li>
 *   <li>POST /api/v1/admin/invoice-number/next   — take the next number for a manual invoice (advances the counter)</li>
 *   <li>PUT  /api/v1/admin/invoice-number?nextValue=N — set the counter so the next number is N</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/invoice-number")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminInvoiceNumberController {

    private final InvoiceService invoiceService;

    @GetMapping
    public ResponseEntity<Counter> get() {
        return ResponseEntity.ok(invoiceService.peekCounter());
    }

    @PostMapping("/next")
    public ResponseEntity<Map<String, String>> takeNext() {
        return ResponseEntity.ok(Map.of("invoiceNumber", invoiceService.nextInvoiceNumber()));
    }

    @PutMapping
    public ResponseEntity<Counter> setCounter(@RequestParam long nextValue) {
        return ResponseEntity.ok(invoiceService.setCounter(nextValue));
    }
}
