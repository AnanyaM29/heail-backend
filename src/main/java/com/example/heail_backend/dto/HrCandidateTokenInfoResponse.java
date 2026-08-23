package com.example.heail_backend.dto;

import lombok.Data;

import java.util.List;

/** Public, token-scoped — what the candidate landing page (no login) shows
 *  before they've accepted terms and redeemed. */
@Data
public class HrCandidateTokenInfoResponse {
    String candidateName;
    List<String> assessmentNames;
    boolean termsAccepted;
}
