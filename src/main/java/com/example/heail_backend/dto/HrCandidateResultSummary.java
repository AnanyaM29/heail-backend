package com.example.heail_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HrCandidateResultSummary {
    short assessmentId;
    String assessmentName;
    boolean completed;
    Short overallScore; // null until completed
    /** True when the attempt was closed by the deadline; overallScore is then the
     *  percentage of marks achieved before time ran out. */
    boolean timedOut;
}
