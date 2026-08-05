package com.example.heail_backend.controller;

import com.example.heail_backend.entity.Order;
import com.example.heail_backend.repository.OrderRepository;
import com.example.heail_backend.service.OrderService;
import com.example.heail_backend.service.OrgOrderService;
import com.example.heail_backend.service.PaypalService;
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
 * PayPal webhook — per spec §4A, "the webhook, never the redirect, is the source of
 * truth." This runs the same idempotent fulfilment as the capture-confirm endpoint,
 * so whichever arrives first does the work and the other is a no-op.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaypalService paypalService;
    private final OrderRepository orderRepo;
    private final OrderService orderService;
    private final OrgOrderService orgOrderService;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String LEADER_CLASSIC_PRODUCT = "LEADER_CLASSIC";

    @PostMapping("/paypal")
    public ResponseEntity<String> paypalWebhook(@RequestHeader HttpHeaders headers, @RequestBody String rawBody) {
        if (!paypalService.verifyWebhookSignature(headers, rawBody)) {
            log.error("Rejected PayPal webhook: signature verification failed");
            return ResponseEntity.status(400).body("invalid signature");
        }

        try {
            JsonNode event = mapper.readTree(rawBody);
            String eventType = event.path("event_type").asText();
            JsonNode resource = event.path("resource");

            switch (eventType) {
                case "PAYMENT.CAPTURE.COMPLETED" -> {
                    String paypalOrderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText(null);
                    String captureId = resource.path("id").asText(null);
                    handleCaptured(paypalOrderId, captureId);
                }
                case "PAYMENT.CAPTURE.DENIED" -> {
                    String paypalOrderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText(null);
                    handleDenied(paypalOrderId);
                }
                default -> log.info("Ignoring PayPal webhook event type: {}", eventType);
            }
            return ResponseEntity.ok("ok");
        } catch (Exception e) {
            log.error("Failed to process PayPal webhook: {}", e.getMessage());
            return ResponseEntity.status(400).body("bad payload");
        }
    }

    private void handleCaptured(String paypalOrderId, String captureId) {
        if (paypalOrderId == null) return;
        Optional<Order> orderOpt = orderRepo.findByGatewayOrderRef(paypalOrderId);
        if (orderOpt.isEmpty()) {
            log.warn("PayPal webhook: no order found for PayPal order id {}", paypalOrderId);
            return;
        }
        Order order = orderOpt.get();
        if (LEADER_CLASSIC_PRODUCT.equals(order.getProductCode())) {
            orderService.markPaidFromGateway(order, captureId);
        } else {
            orgOrderService.markPaidFromGateway(order, captureId);
        }
    }

    private void handleDenied(String paypalOrderId) {
        if (paypalOrderId == null) return;
        Optional<Order> orderOpt = orderRepo.findByGatewayOrderRef(paypalOrderId);
        if (orderOpt.isEmpty()) return;
        Order order = orderOpt.get();
        if (LEADER_CLASSIC_PRODUCT.equals(order.getProductCode())) {
            orderService.markFailedFromGateway(order);
        } else {
            orgOrderService.markFailedFromGateway(order);
        }
    }
}
