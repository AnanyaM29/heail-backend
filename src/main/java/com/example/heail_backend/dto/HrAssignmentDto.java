package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One HR pillar assignment — i.e. one entitlement — for the logged-in person.
 * The same pillar can legitimately be assigned to the same person more than
 * once (registered as a candidate on two separate orders), and each such
 * assignment gets its own card: its own assignedAt, its own attempt, its own
 * status. Never merged into a single "HR_A3" row the way the pillar catalogue
 * (HrAssessmentDto) is — see HrAssessmentService.listAssignments.
 */
@Data
public class HrAssignmentDto {
    /** The entitlement backing this assignment — stable per-assignment identity
     *  for the frontend to track/key on. */
    UUID entitlementId;

    short assessmentId;
    String assessmentCode;
    String assessmentName;
    short questionCount;
    short timeMinutes;

    /** When this specific assignment (entitlement) was granted. */
    LocalDateTime assignedAt;

    /** 1-based position among this pillar's assignments to this person, oldest
     *  first — matches the session's attemptNumber once it's been started. */
    int attemptNumber;

    /** PENDING (not started yet) | IN_PROGRESS | COMPLETED */
    String status;

    /** Set once started (IN_PROGRESS or COMPLETED). */
    UUID sessionId;

    /** Only meaningful once COMPLETED. */
    boolean timedOut;
}
