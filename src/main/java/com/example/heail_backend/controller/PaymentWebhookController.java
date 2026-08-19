package com.example.heail_backend.controller;

import com.example.heail_backend.entity.Order;
import com.example.heail_backend.repository.OrderRepository;
import com.example.heail_backend.service.OrderService;
import com.example.heail_backend.service.OrgOrderService;
import com.example.heail_backend.service.RazorpayService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Razorpay webhook — per spec §4A, "the webhook, never the redirect, is the source of
 * truth." This runs the same idempotent fulfilment as the capture-confirm endpoint,
 * so whichever arrives first does the work and the other is a no-op.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final RazorpayService razorpayService;
    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final OrgOrderService orgOrderService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String LEADER_CLASSIC_PRODUCT = "LEADER_CLASSIC";

    @PostMapping("/razorpay")
    public ResponseEntity<String> razorpayWebhook(@RequestHeader HttpHeaders headers, @RequestBody String rawBody) {
        String signature = headers.getFirst("x-razorpay-signature");
        if (!razorpayService.verifyWebhookSignature(rawBody, signature)) {
            log.error("Rejected Razorpay webhook: signature verification failed");
            return ResponseEntity.status(400).body("invalid signature");
        }

        try {
            JsonNode event = mapper.readTree(rawBody);
            String eventType = event.path("event").asText();
            JsonNode payment = event.path("payload").path("payment").path("entity");

            switch (eventType) {
                case "payment.captured" -> {
                    String razorpayOrderId = payment.path("order_id").asText(null);
                    String paymentId = payment.path("id").asText(null);
                    handleCaptured(razorpayOrderId, paymentId);
                }
                case "payment.failed" -> {
                    String razorpayOrderId = payment.path("order_id").asText(null);
                    handleDenied(razorpayOrderId);
                }
                default -> log.info("Ignoring Razorpay webhook event type: {}", eventType);
            }
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Failed to process Razorpay webhook: {}", e.getMessage());
            return ResponseEntity.status(400).body("bad payload");
        }
    }

    private void handleCaptured(String razorpayOrderId, String paymentId) {
        if (razorpayOrderId == null) return;
        Optional<Order> orderOpt = orderRepo.findByGatewayOrderRef(razorpayOrderId);
        if (orderOpt.isEmpty()) {
            log.warn("Razorpay webhook: no order found for order id {}", razorpayOrderId);
            return;
        }
        Order order = orderOpt.get();
        if (LEADER_CLASSIC_PRODUCT.equals(order.getProductCode())) {
            orderService.markPaidFromGateway(order, paymentId);
        } else {
            orgOrderService.markPaidFromGateway(order, paymentId);
        }
    }

    private void handleDenied(String razorpayOrderId) {
        if (razorpayOrderId == null) return;
        Optional<Order> orderOpt = orderRepo.findByGatewayOrderRef(razorpayOrderId);
        if (orderOpt.isEmpty()) return;
        Order order = orderOpt.get();
        if (LEADER_CLASSIC_PRODUCT.equals(order.getProductCode())) {
            orderService.markFailedFromGateway(order);
        } else {
            orgOrderService.markFailedFromGateway(order);
        }
    }
}
