package com.example.heail_backend.service;

import com.example.heail_backend.dto.*;
import com.example.heail_backend.entity.*;
import com.example.heail_backend.exception.EmployeeBatchValidationException;
import com.example.heail_backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Purchase flow for the HR Competency Assessment product — a buyer (HR/
 * recruiter) picks a subset of the 7 pillars, registers the candidates who
 * will actually take them (every candidate takes every selected pillar),
 * then pays. Not self-serve: the buyer's own account never gets an
 * entitlement here, only their candidates do (see fulfil()).
 *
 * Price is pillars × candidate count, so candidates are entered *before*
 * payment — same ordering as OrgOrderService's roster-then-pay flow, for the
 * same reason (price depends on headcount). selectAssessments() and
 * setCandidates() are the two draft-mutating steps, mirroring
 * OrgOrderService.setOrgDetails()/setEmployees(); pillar selection is
 * captured on Order.metadata (see SELECTED_IDS_KEY), same free-form JSON
 * column Leader already uses for designation/org name.
 *
 * Deliberately duplicated from OrderService/OrgOrderService rather than
 * generalizing them — each purchase shape's fulfilment differs enough that
 * sharing the state-machine code would mean threading product-specific
 * branches through every method anyway.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrOrderService {

    private static final String HR_SUITE_PRODUCT = "HR_SUITE";
    private static final String HR_PILLAR_PRICING_CODE = "HR_PILLAR"; // per-pillar unit price lookup
    private static final String HR_PURCHASE_AGREEMENT = "HR_PURCHASE";
    private static final String INR_CURRENCY = "INR";
    private static final String SELECTED_IDS_KEY = "selectedAssessmentIds";
    private static final String REALLOCATES_CANDIDATE_KEY = "reallocatesCandidateId";
    private static final String ENTITLEMENT_PREFIX = "HR_A";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final OrderRepository orderRepo;
    private final ConsentLogRepository consentRepo;
    private final UserRepository userRepo;
    private final PricingItemRepository pricingRepo;
    private final EmailService emailService;
    private final EntitlementRepository entitlementRepo;
    private final RazorpayService razorpayService;
    private final InvoiceService invoiceService;
    private final HrAssessmentRepository hrAssessmentRepo;
    private final HrCandidateRepository hrCandidateRepo;
    private final HrResultRepository hrResultRepo;
    private final PasswordEncoder encoder;
    private final DiscountCouponService couponService;

    @Value("${app.payments.razorpay-enabled:false}")
    private boolean razorpayEnabled;

    /* ── Set (or change) which pillars this order covers ──────────── */
    @Transactional
    public OrderResponse selectAssessments(String email, List<Short> assessmentIds) {
        if (assessmentIds == null || assessmentIds.isEmpty())
            throw new IllegalArgumentException("Select at least one assessment");
        List<Short> distinctIds = assessmentIds.stream().distinct().sorted().toList();

        List<HrAssessment> found = hrAssessmentRepo.findAllById(distinctIds);
        if (found.size() != distinctIds.size())
            throw new IllegalArgumentException("One or more selected assessments don't exist");

        User user = requireUser(email);
        List<Order> existing = orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, HR_SUITE_PRODUCT);
        Order latest = existing.isEmpty() ? null : existing.get(0);

        // Same reuse rule as Leader: only ever reuse a still-DRAFT order. Anything
        // further along but never PAID is treated as a dead, interrupted attempt.
        boolean reusable = latest != null && latest.getStatus() == OrderStatus.DRAFT;

        Order order = reusable ? latest : new Order();
        order.setUser(user);
        order.setProductCode(HR_SUITE_PRODUCT);
        order.setCurrency(INR_CURRENCY);
        order.setStatus(OrderStatus.DRAFT);

        Map<String, String> metadata = order.getMetadata() != null ? new HashMap<>(order.getMetadata()) : new HashMap<>();
        metadata.put(SELECTED_IDS_KEY, serializeIds(distinctIds));
        order.setMetadata(metadata);

        // Price is pillars × candidates, but candidates are entered on the *next*
        // step (setCandidates) — reprice against whatever's already on this draft
        // (0 for a brand-new order, or the existing roster if the buyer came back
        // to change their pillar selection after already entering candidates).
        int candidateCount = reusable ? hrCandidateRepo.findByOrder(latest).size() : 0;
        repriceForCandidates(order, distinctIds.size(), candidateCount);

        order = orderRepo.save(order);
        return toResponse(order);
    }

    /** Recomputes amount/GST for pillars × candidates, INR only (Razorpay is the only gateway). */
    private void repriceForCandidates(Order order, int pillarCount, int candidateCount) {
        if (candidateCount == 0) {
            order.setAmount(BigDecimal.ZERO);
            order.setGstAmount(BigDecimal.ZERO);
            return;
        }
        PricingItem pricing = pricingRepo.findByProductCodeAndCurrencyAndActiveTrue(HR_PILLAR_PRICING_CODE, INR_CURRENCY)
                .orElseThrow(() -> new IllegalStateException("No active price configured for " + HR_PILLAR_PRICING_CODE));

        BigDecimal amount = pricing.getAmount().multiply(BigDecimal.valueOf((long) pillarCount * candidateCount));
        BigDecimal gstAmount = amount.multiply(pricing.getGstPct()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        order.setAmount(amount);
        order.setGstAmount(gstAmount);
        applyFeeDiscount(order);
    }

    /** Admin-granted, permanent fee discount (see User.feeDiscountPercent) — scales the
     *  order's freshly-computed price down by this percentage. Always called right after
     *  amount/gstAmount are recomputed from scratch (never on a value that might already
     *  be discounted), so repeat calls can't compound the discount. */
    private void applyFeeDiscount(Order order) {
        int pct = order.getUser().getFeeDiscountPercent();
        if (pct <= 0) return;
        BigDecimal factor = BigDecimal.valueOf(100 - pct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        order.setAmount(order.getAmount().multiply(factor).setScale(2, RoundingMode.HALF_UP));
        order.setGstAmount(order.getGstAmount().multiply(factor).setScale(2, RoundingMode.HALF_UP));
    }

    /* ── Replace the order's candidate roster, all-or-nothing — every
       candidate takes every pillar selected on the order (see class doc) ── */
    @Transactional
    public HrOrderResponse setCandidates(UUID orderId, String email, List<CandidateRowRequest> rows) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("Candidates can only be set on a draft order");

        List<Short> selectedIds = deserializeIds(
                order.getMetadata() != null ? order.getMetadata().get(SELECTED_IDS_KEY) : null);
        if (selectedIds.isEmpty())
            throw new IllegalArgumentException("Select at least one assessment before adding candidates");

        if (rows == null || rows.isEmpty())
            throw new IllegalArgumentException("Add at least one candidate");

        List<RowError> errors = new ArrayList<>();
        List<String> seenEmails = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < rows.size(); i++) {
            CandidateRowRequest row = rows.get(i);
            int rowNumber = i + 1;

            if (row.getName() == null || row.getName().isBlank()) {
                errors.add(new RowError(rowNumber, "Name is required"));
                continue;
            }
            if (row.getEmail() == null || !EMAIL_PATTERN.matcher(row.getEmail().trim()).matches()) {
                errors.add(new RowError(rowNumber, "A valid email is required"));
                continue;
            }
            String normalisedEmail = row.getEmail().trim().toLowerCase();
            if (seenEmails.contains(normalisedEmail)) {
                errors.add(new RowError(rowNumber, "Duplicate email within this submission"));
                continue;
            }
            if (row.getDob() == null) {
                errors.add(new RowError(rowNumber, "Date of birth is required"));
                continue;
            }
            if (row.getMobile() == null || row.getMobile().trim().length() < 6) {
                errors.add(new RowError(rowNumber, "A valid mobile number is required"));
                continue;
            }
            if (row.getAssessmentStartDate() == null || row.getAssessmentStartDate().isBefore(today)) {
                errors.add(new RowError(rowNumber, "Assessment start date is required and can't be in the past"));
                continue;
            }
            seenEmails.add(normalisedEmail);
        }

        if (!errors.isEmpty()) throw new EmployeeBatchValidationException(errors);

        hrCandidateRepo.deleteByOrder(order);

        List<HrCandidate> candidates = new ArrayList<>();
        for (CandidateRowRequest row : rows) {
            HrCandidate c = new HrCandidate();
            c.setOrder(order);
            c.setName(row.getName().trim());
            c.setDob(row.getDob());
            c.setEmail(row.getEmail().trim().toLowerCase());
            c.setMobile(row.getMobile().trim());
            c.setAssessmentStartDate(row.getAssessmentStartDate());
            c.setStatus("PENDING");
            candidates.add(c);
        }
        hrCandidateRepo.saveAll(candidates);

        repriceForCandidates(order, selectedIds.size(), candidates.size());
        order = orderRepo.save(order);

        return toHrOrderResponse(order);
    }

    /* ── Read the order + its candidate roster (candidate-entry screen, and
       revisits before payment) ────────────────────────────────────────── */
    @Transactional(readOnly = true)
    public HrOrderResponse getOrderWithCandidates(UUID orderId, String email) {
        return toHrOrderResponse(requireOwnedOrder(orderId, email));
    }

    /* ── Every candidate the buyer has ever registered, across every PAID
       order, most recent first — the buyer-facing tracking view. Results are
       looked up by the candidate's linked User, same as
       HrAssessmentService.listResults(). ─────────────────────────────── */
    @Transactional(readOnly = true)
    public List<HrCandidateDto> listMyCandidates(String email) {
        User buyer = requireUser(email);
        return hrCandidateRepo.findByOrderUserOrderByCreatedAtDesc(buyer).stream()
                .filter(c -> c.getOrder().getStatus() == OrderStatus.PAID)
                .map(c -> {
                    HrCandidateDto dto = toCandidateDto(c);
                    // Both are now self-service paid actions (a fresh mini-order, same
                    // pillars) rather than a free admin-vetted swap — see
                    // createReallocationOrder/createRetakeOrder — so there's no time
                    // window to check, just whether the action makes sense for this
                    // candidate's current state.
                    dto.setCanRequestReallocation("SENT".equals(c.getStatus()));
                    dto.setCanRequestRetake(c.getUser() != null && !"PENDING".equals(c.getStatus()));
                    if (c.getUser() != null) {
                        dto.setResults(hrResultRepo.findByUserOrderByCreatedAtDesc(c.getUser()).stream()
                                .map(r -> new HrCandidateResultSummary(r.getAssessment().getId(),
                                        r.getAssessment().getName(), true, r.getOverallScore()))
                                .toList());
                    }
                    return dto;
                }).toList();
    }

    /* ── Reallocation / retake — both are self-service: buy a fresh
       single-candidate order for the same pillars, and pay through the
       existing HrPaymentComponent/markPaidFromGateway pipeline unchanged.
       No admin approval — see class doc for why the earlier request/approve
       design was retired. ─────────────────────────────────────────────── */

    /** Retake: same candidate, same details, a brand new paid slot — their
     *  existing account is reused (matched by email) at fulfilment time, so
     *  this just grants a fresh entitlement + a live token even if their old
     *  one expired. */
    @Transactional
    public OrderResponse createRetakeOrder(UUID candidateId, String email) {
        HrCandidate original = requireOwnedCandidate(candidateId, email);
        if (original.getUser() == null)
            throw new IllegalArgumentException("This candidate hasn't been sent an invitation yet");

        Order newOrder = createFollowOnOrder(original.getOrder(), original.getName(), original.getDob(),
                original.getEmail(), original.getMobile(), null);
        return toResponse(newOrder);
    }

    /** Reallocation: a different person takes over an unstarted candidate's
     *  slot. The old candidate's own link is invalidated once this new order
     *  is actually paid (see markPaidFromGateway) — not now, so an abandoned
     *  checkout doesn't kill a still-valid invitation. */
    @Transactional
    public OrderResponse createReallocationOrder(UUID candidateId, String email, ReallocationRequestDto newDetails) {
        HrCandidate original = requireOwnedCandidate(candidateId, email);
        if (!"SENT".equals(original.getStatus()))
            throw new IllegalArgumentException("Only an unstarted candidate can be reallocated");
        if (newDetails == null || newDetails.getNewName() == null || newDetails.getNewName().isBlank()
                || newDetails.getNewEmail() == null || !EMAIL_PATTERN.matcher(newDetails.getNewEmail().trim()).matches()
                || newDetails.getNewDob() == null)
            throw new IllegalArgumentException("The replacement candidate's name, DOB and a valid email are required");

        Order newOrder = createFollowOnOrder(original.getOrder(), newDetails.getNewName().trim(), newDetails.getNewDob(),
                newDetails.getNewEmail().trim().toLowerCase(), newDetails.getNewMobile(), original);
        return toResponse(newOrder);
    }

    /** Shared by both: a new DRAFT order, same pillars as the original, one
     *  candidate row, priced and ready to route straight into
     *  HrPaymentComponent (skips candidate-entry — there's only ever one row). */
    private Order createFollowOnOrder(Order originalOrder, String name, LocalDate dob, String email, String mobile,
                                       HrCandidate replaces) {
        List<Short> selectedIds = selectedPillarIds(originalOrder);
        if (selectedIds.isEmpty())
            throw new IllegalStateException("The original order has no pillars on record");

        Order order = new Order();
        order.setUser(originalOrder.getUser());
        order.setProductCode(HR_SUITE_PRODUCT);
        order.setCurrency(INR_CURRENCY);
        order.setStatus(OrderStatus.DRAFT);

        Map<String, String> metadata = new HashMap<>();
        metadata.put(SELECTED_IDS_KEY, serializeIds(selectedIds));
        if (replaces != null) metadata.put(REALLOCATES_CANDIDATE_KEY, replaces.getId().toString());
        order.setMetadata(metadata);
        order = orderRepo.save(order);

        HrCandidate candidate = new HrCandidate();
        candidate.setOrder(order);
        candidate.setName(name);
        candidate.setDob(dob);
        candidate.setEmail(email);
        candidate.setMobile(mobile);
        candidate.setAssessmentStartDate(LocalDate.now());
        candidate.setStatus("PENDING");
        hrCandidateRepo.save(candidate);

        repriceForCandidates(order, selectedIds.size(), 1);
        return orderRepo.save(order);
    }

    private HrCandidate requireOwnedCandidate(UUID candidateId, String email) {
        HrCandidate candidate = hrCandidateRepo.findById(candidateId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
        if (!candidate.getOrder().getUser().getEmail().equalsIgnoreCase(email))
            throw new IllegalArgumentException("Candidate not found");
        return candidate;
    }

    /* ── Accept the Terms of Agreement for an order ───────────────── */
    @Transactional
    public OrderResponse acceptAgreement(UUID orderId, String email, String version) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.DRAFT)
            throw new IllegalArgumentException("Agreement can only be accepted on a draft order");
        if (hrCandidateRepo.findByOrder(order).isEmpty())
            throw new IllegalArgumentException("Add candidates before accepting the agreement");

        order.setStatus(OrderStatus.AGREEMENT_ACCEPTED);
        order.setAgreementAcceptedAt(LocalDateTime.now());
        order = orderRepo.save(order);

        ConsentLog consent = new ConsentLog();
        consent.setUser(order.getUser());
        consent.setAgreementType(HR_PURCHASE_AGREEMENT);
        consent.setVersion(version);
        consentRepo.save(consent);

        return toResponse(order);
    }

    /* ── Apply a discount coupon before accepting the agreement ───── */
    @Transactional
    public OrderResponse applyCoupon(UUID orderId, String email, String code) {
        Order order = requireOwnedOrder(orderId, email);
        couponService.applyToOrder(order, code, email);
        order = orderRepo.save(order);
        return toResponse(order);
    }

    /* ── Create a real Razorpay order once the agreement is accepted ── */
    @Transactional
    public OrderResponse createRazorpayOrder(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() != OrderStatus.AGREEMENT_ACCEPTED)
            throw new IllegalArgumentException("Accept the agreement before paying");

        if (!razorpayEnabled) {
            log.warn("Razorpay disabled — completing order {} without a real payment", order.getId());
            markPaidFromGateway(order, null);
            return toResponse(order);
        }

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            log.info("Order {} is fully covered by coupon {} — skipping Razorpay", order.getId(), order.getCouponCode());
            markPaidFromGateway(order, null);
            return toResponse(order);
        }

        String razorpayOrderId = razorpayService.createOrder(total, order.getCurrency(), order.getId().toString());

        order.setStatus(OrderStatus.PAYMENT_INITIATED);
        order.setPaymentInitiatedAt(LocalDateTime.now());
        order.setGatewayOrderRef(razorpayOrderId);
        order = orderRepo.save(order);

        return toResponse(order);
    }

    /* ── Verify the Razorpay payment signature Checkout.js hands back client-side ── */
    @Transactional
    public OrderResponse verifyRazorpayPayment(UUID orderId, String email, String razorpayOrderId,
                                                String razorpayPaymentId, String razorpaySignature) {
        Order order = requireOwnedOrder(orderId, email);
        if (order.getStatus() == OrderStatus.PAID) return toResponse(order); // already fulfilled, e.g. by the webhook

        if (order.getStatus() != OrderStatus.PAYMENT_INITIATED || !razorpayOrderId.equals(order.getGatewayOrderRef()))
            throw new IllegalArgumentException("No matching payment in progress for this order");

        if (!razorpayService.verifyPaymentSignature(razorpayOrderId, razorpayPaymentId, razorpaySignature)) {
            order.setStatus(OrderStatus.FAILED);
            orderRepo.save(order);
            throw new IllegalStateException("Payment signature verification failed");
        }

        markPaidFromGateway(order, razorpayPaymentId);
        return toResponse(order);
    }

    /** Test-mode-only shortcut — same gate as OrderService: only fires for rzp_test_* keys. */
    @Transactional
    public OrderResponse forceCompleteTestPayment(UUID orderId, String email) {
        Order order = requireOwnedOrder(orderId, email);
        if (!razorpayService.isTestMode())
            throw new IllegalStateException("Live payments are enabled — this isn't available");
        if (order.getStatus() != OrderStatus.PAYMENT_INITIATED)
            throw new IllegalArgumentException("No payment in progress for this order");

        markPaidFromGateway(order, null);
        return toResponse(order);
    }

    /**
     * Idempotent fulfilment — shared by the capture endpoint and the Razorpay webhook,
     * same pattern as OrderService.markPaidFromGateway. The one real difference: this
     * grants one Entitlement *per selected pillar*, not a single entitlement matching
     * the order's own product code (HR_SUITE is the order's product; HR_A{n} is what
     * actually gates starting a given pillar — see HrAssessmentService).
     */
    @Transactional
    public void markPaidFromGateway(Order order, String paymentId) {
        if (order.getStatus() == OrderStatus.PAID) return;

        order.setStatus(OrderStatus.PAID);
        Map<String, String> metadata = order.getMetadata() != null ? new HashMap<>(order.getMetadata()) : new HashMap<>();
        if (paymentId != null) metadata.put("razorpayPaymentId", paymentId);
        order.setMetadata(metadata);
        order.setPaidAt(LocalDateTime.now());

        BigDecimal total = order.getAmount().add(order.getGstAmount());
        boolean freeViaCoupon = total.compareTo(BigDecimal.ZERO) == 0;
        if (!freeViaCoupon) order.setInvoiceNumber(invoiceService.nextInvoiceNumber());
        order = orderRepo.save(order);

        List<Short> selectedIds = deserializeIds(metadata.get(SELECTED_IDS_KEY));
        List<HrCandidate> candidates = hrCandidateRepo.findByOrder(order);
        fulfilCandidates(order, candidates, selectedIds);

        // A reallocation order: now that payment actually went through, retire
        // the candidate this one replaces — not at order-creation time, so an
        // abandoned checkout never kills a still-valid invitation.
        String reallocatesId = metadata.get(REALLOCATES_CANDIDATE_KEY);
        if (reallocatesId != null) {
            hrCandidateRepo.findById(UUID.fromString(reallocatesId)).ifPresent(old -> {
                old.setStatus("REALLOCATED");
                hrCandidateRepo.save(old);
            });
        }

        if (freeViaCoupon) {
            emailService.sendHrFreeAccessGranted(order.getUser().getEmail(), order.getUser().getName(), candidates.size());
        } else {
            String amountDisplay = order.getCurrency() + " " + total.setScale(2, RoundingMode.HALF_UP);
            byte[] invoicePdf = invoiceService.generate(order, order.getUser().getName(), order.getUser().getEmail());
            emailService.sendInvoice(order.getUser().getEmail(), order.getUser().getName(),
                    amountDisplay, invoicePdf, order.getInvoiceNumber());
        }
    }

    /**
     * Per candidate: find-or-create a throwaway User (same shape as
     * OrgOrderService.fulfil() — random unusable password, candidate never
     * sees it), grant one Entitlement per selected pillar to that user (not
     * the buyer), generate the access token + 7-day expiry window, and email
     * the invite. A failure on one candidate doesn't block the rest — same
     * per-row try/catch reasoning as OrgOrderService.fulfil().
     */
    private void fulfilCandidates(Order order, List<HrCandidate> candidates, List<Short> selectedIds) {
        List<String> pillarNames = hrAssessmentRepo.findAllById(selectedIds).stream()
                .map(HrAssessment::getName).toList();

        for (HrCandidate candidate : candidates) {
            try {
                fulfilOneCandidate(order, candidate, selectedIds, pillarNames);
            } catch (Exception e) {
                log.error("Failed to fulfil HR candidate {}: {}", candidate.getEmail(), e.getMessage());
            }
        }
    }

    /** One candidate's worth of the fulfilment above. */
    private void fulfilOneCandidate(Order order, HrCandidate candidate, List<Short> selectedIds, List<String> pillarNames) {
        User user = userRepo.findByEmail(candidate.getEmail()).orElse(null);
        if (user == null) {
            user = new User();
            user.setName(candidate.getName());
            user.setEmail(candidate.getEmail());
            user.setPasswordHash(encoder.encode(UUID.randomUUID().toString()));
            user.setRole("EMPLOYEE");
            user.setMobile(candidate.getMobile());
            user = userRepo.save(user);
        }
        candidate.setUser(user);

        for (Short assessmentId : selectedIds) {
            Entitlement entitlement = new Entitlement();
            entitlement.setUser(user);
            entitlement.setProductCode(ENTITLEMENT_PREFIX + assessmentId);
            entitlement.setSource(EntitlementSource.PURCHASE);
            entitlement.setOrder(order);
            entitlement.setUsed(false);
            entitlementRepo.save(entitlement);
        }

        candidate.setAccessToken(generateAccessToken());
        candidate.setTokenExpiresAt(candidate.getAssessmentStartDate().atStartOfDay().plusDays(7));
        candidate.setStatus("SENT");
        hrCandidateRepo.save(candidate);

        emailService.sendCandidateInvitation(candidate.getEmail(), candidate.getName(), pillarNames,
                candidate.getAccessToken(), candidate.getTokenExpiresAt(), order.getUser().getEmail());
    }

    /** Pillar IDs currently selected on an order — used when building a
     *  follow-on (retake/reallocation) order for the same pillars. */
    private List<Short> selectedPillarIds(Order order) {
        return deserializeIds(order.getMetadata() != null ? order.getMetadata().get(SELECTED_IDS_KEY) : null);
    }

    private String generateAccessToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void markFailedFromGateway(Order order) {
        if (order.getStatus() == OrderStatus.PAID) return;
        order.setStatus(OrderStatus.FAILED);
        orderRepo.save(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, String email) {
        return toResponse(requireOwnedOrder(orderId, email));
    }

    /* ── Private helpers ───────────────────────────────────────── */
    private String serializeIds(List<Short> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Short> deserializeIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(Short::parseShort).toList();
    }

    private Order requireOwnedOrder(UUID orderId, String email) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        if (!order.getUser().getEmail().equalsIgnoreCase(email))
            throw new IllegalArgumentException("Order not found");
        return order;
    }

    private User requireUser(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse res = new OrderResponse();
        res.setId(order.getId());
        res.setProduct(order.getProductCode());
        res.setBaseAmount(order.getAmount());
        res.setGstAmount(order.getGstAmount());
        res.setTotalAmount(order.getAmount().add(order.getGstAmount()));
        res.setCurrency(order.getCurrency());
        res.setStatus(order.getStatus().name());
        res.setAgreementAcceptedAt(order.getAgreementAcceptedAt());
        res.setGatewayReference(order.getGatewayOrderRef());
        res.setInvoiceNumber(order.getInvoiceNumber());
        res.setPaidAt(order.getPaidAt());
        res.setCreatedAt(order.getDraftAt());
        res.setMetadata(order.getMetadata());
        res.setRazorpayKeyId(razorpayService.getKeyId());
        res.setCouponCode(order.getCouponCode());
        res.setDiscountPercent(order.getDiscountPercent());
        return res;
    }

    private HrOrderResponse toHrOrderResponse(Order order) {
        HrOrderResponse res = new HrOrderResponse();
        res.setOrder(toResponse(order));
        List<Short> selectedIds = deserializeIds(
                order.getMetadata() != null ? order.getMetadata().get(SELECTED_IDS_KEY) : null);
        res.setSelectedAssessmentNames(hrAssessmentRepo.findAllById(selectedIds).stream()
                .map(HrAssessment::getName).toList());
        res.setCandidates(hrCandidateRepo.findByOrder(order).stream().map(this::toCandidateDto).toList());
        return res;
    }

    private HrCandidateDto toCandidateDto(HrCandidate c) {
        HrCandidateDto dto = new HrCandidateDto();
        dto.setId(c.getId());
        dto.setOrderId(c.getOrder().getId());
        dto.setName(c.getName());
        dto.setDob(c.getDob());
        dto.setEmail(c.getEmail());
        dto.setMobile(c.getMobile());
        dto.setAssessmentStartDate(c.getAssessmentStartDate());
        dto.setStatus(c.getStatus());
        dto.setTokenExpiresAt(c.getTokenExpiresAt());
        return dto;
    }
}
