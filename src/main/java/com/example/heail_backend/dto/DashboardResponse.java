package com.example.heail_backend.dto;

import lombok.Data;

import java.util.List;

/** Everything the caller is doing across every HEAIL product, keyed off their
 *  single account (user_id/email) rather than their stored role — a person can
 *  simultaneously be an org admin, an individual Leader customer, and/or a pulse
 *  respondent in one or more organisations' rounds. Each section is populated
 *  independently and may be empty; the frontend should only render sections that
 *  have data. */
@Data
public class DashboardResponse {
    String email;
    String name;

    /** SUITE_4PULSE rounds this person owns/administers. */
    List<OrgOrderResponse> organisationsAdministered;

    /** LEADER_CLASSIC (The Gita Leader) completed attempts. */
    List<LeaderResultResponse> leaderResults;

    /** Every one of the caller's own unfinished LEADER_CLASSIC attempts — usually
     *  0 or 1, but each purchase starts its own independent session, so more than
     *  one can be in progress at once (e.g. a fresh purchase started while an
     *  older attempt was left unfinished). Each gets its own card. */
    List<SessionResumeResponse> leaderInProgress;

    /** True if the caller has a LEADER_CLASSIC order that hasn't been paid for yet
     *  (DRAFT/AGREEMENT_ACCEPTED/PAYMENT_INITIATED) — lets the dashboard surface a
     *  "complete payment" prompt even before any assessment session exists. */
    boolean leaderUnpaidOrder;

    /** True if the caller holds an unused LEADER_CLASSIC entitlement — i.e. they've
     *  paid (or redeemed a 100% coupon) but not yet started the assessment. Without
     *  this a paid-but-unstarted Leader purchase is invisible on the dashboard. */
    boolean leaderReadyToStart;

    /** Every organisation's pulse round the caller has been invited into as a
     *  respondent (their own org and/or any other org that added them by email). */
    List<RespondentMembershipDto> respondentMemberships;

    /** Every individual HR assignment (entitlement) this person holds, across
     *  all 7 pillars — one entry per assignment. The same pillar shows up as
     *  more than one entry if it was assigned to them more than once (e.g.
     *  registered as a candidate on two separate orders). */
    List<HrAssignmentDto> hrAssignments;
}
