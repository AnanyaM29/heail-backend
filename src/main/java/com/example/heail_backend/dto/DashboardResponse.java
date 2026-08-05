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

    /** The caller's own in-progress LEADER_CLASSIC session, if any. */
    SessionResumeResponse leaderInProgress;

    /** Every organisation's pulse round the caller has been invited into as a
     *  respondent (their own org and/or any other org that added them by email). */
    List<RespondentMembershipDto> respondentMemberships;
}
