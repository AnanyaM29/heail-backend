package com.example.heail_backend.service;

import com.example.heail_backend.dto.DashboardResponse;
import com.example.heail_backend.dto.RespondentMembershipDto;
import com.example.heail_backend.entity.AssessmentSession;
import com.example.heail_backend.entity.Order;
import com.example.heail_backend.entity.OrderEmployee;
import com.example.heail_backend.entity.OrderStatus;
import com.example.heail_backend.entity.SessionStatus;
import com.example.heail_backend.entity.User;
import com.example.heail_backend.repository.AssessmentSessionRepository;
import com.example.heail_backend.repository.OrderEmployeeRepository;
import com.example.heail_backend.repository.OrderRepository;
import com.example.heail_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Aggregates a single person's activity across every HEAIL product into one view.
 *
 * Deliberately does NOT gate anything by User.role: that column only ever holds one
 * value platform-wide, so a person who is (for example) an org admin AND also a
 * pulse respondent in their own or another organisation's round would otherwise have
 * half their activity invisible to them. Every section here is looked up independently
 * by user_id/email, matching how the underlying tables already link back to the same
 * account (see OrgOrderService.fulfil()).
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final List<String> PULSE_SEQUENCE =
            List.of("LEADER_PULSE", "TALENT_PULSE", "SYSTEM_PULSE", "GROWTH_PULSE");
    private static final String LEADER_CLASSIC_PRODUCT = "LEADER_CLASSIC";

    private final UserRepository userRepo;
    private final OrderEmployeeRepository orderEmployeeRepo;
    private final AssessmentSessionRepository sessionRepo;
    private final OrderRepository orderRepo;
    private final OrgOrderService orgOrderService;
    private final AssessmentService assessmentService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        DashboardResponse res = new DashboardResponse();
        res.setEmail(user.getEmail());
        res.setName(user.getName());

        // Org-admin activity — every SUITE_4PULSE round this person has created/paid for.
        // Filtered to drop (a) untouched DRAFT orders with 0 employees — abandoned clicks
        // through the buy-org flow, not a real "organisation you administer" — and
        // (b) explicitly cancelled (ABANDONED) rounds. Both otherwise pile up as
        // confusing duplicate-looking cards.
        res.setOrganisationsAdministered(
                orgOrderService.listMyOrders(email).stream()
                        .filter(o -> !OrderStatus.ABANDONED.name().equals(o.getOrder().getStatus()))
                        .filter(o -> !OrderStatus.DRAFT.name().equals(o.getOrder().getStatus()) || !o.getEmployees().isEmpty())
                        .toList()
        );

        // Individual Leader activity — completed attempts and any session in progress.
        res.setLeaderResults(assessmentService.listResults(email));
        res.setLeaderInProgress(assessmentService.current(email).orElse(null));

        List<Order> leaderOrders = orderRepo.findByUserAndProductCodeOrderByDraftAtDesc(user, LEADER_CLASSIC_PRODUCT);
        res.setLeaderUnpaidOrder(!leaderOrders.isEmpty() && leaderOrders.get(0).getStatus() != OrderStatus.PAID
                && leaderOrders.get(0).getStatus() != OrderStatus.ABANDONED && leaderOrders.get(0).getStatus() != OrderStatus.FAILED);

        // Respondent activity — every org's pulse round this person was invited into,
        // whether as an employee of their own org or added by a different one.
        res.setRespondentMemberships(
                orderEmployeeRepo.findByUser(user).stream()
                        .map(this::toMembership)
                        .toList()
        );

        return res;
    }

    private RespondentMembershipDto toMembership(OrderEmployee membership) {
        Order order = membership.getOrder();
        User admin = order.getUser();

        RespondentMembershipDto dto = new RespondentMembershipDto();
        dto.setOrderId(order.getId());
        dto.setOrganisationName(
                admin.getOrganisation() != null ? admin.getOrganisation().getName()
                        : admin.getName() + "'s organisation");
        dto.setLevel(membership.getLevel());
        dto.setInvitationStatus(membership.getInvitationStatus());
        dto.setOrderStatus(order.getStatus().name());
        dto.setPaidAt(order.getPaidAt());

        int completed = 0;
        for (String pulseCode : PULSE_SEQUENCE) {
            List<AssessmentSession> sessions =
                    sessionRepo.findByUserAndOrderAndPulse(membership.getUser(), order, pulseCode);
            if (sessions.stream().anyMatch(s -> s.getStatus() == SessionStatus.COMPLETED)) {
                completed++;
            }
        }
        dto.setPulsesCompleted(completed);
        dto.setPulsesTotal(PULSE_SEQUENCE.size());

        return dto;
    }
}
