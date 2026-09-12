package com.example.heail_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class HrAssessmentDto {
    short id;
    String code;
    String name;
    short questionCount;
    short timeMinutes;
    boolean entitled;
    /** Set when there's a live IN_PROGRESS session for this pillar. A pillar
     *  stays visible/resumable once started even though start() has already
     *  consumed its entitlement (entitled flips to false) — see
     *  HrAssessmentService.listAssessments. */
    UUID inProgressSessionId;
    /** When this pillar's entitlement was granted to the candidate — null if
     *  they were never entitled to it at all. */
    LocalDateTime assignedAt;
}
