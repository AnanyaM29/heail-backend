package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** One organisation's pulse round the caller has been invited into as a respondent
 *  (level L/MM/E) — separate from any round they might administer themselves. */
@Data
public class RespondentMembershipDto {
    UUID orderId;
    String organisationName;
    String level;
    String invitationStatus;
    String orderStatus;
    LocalDateTime paidAt;
    int pulsesCompleted;
    int pulsesTotal;
}
