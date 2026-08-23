package com.example.heail_backend.dto;

import lombok.Data;

import java.util.List;

/** Order + its candidate roster combined — mirrors OrgOrderResponse's shape
 *  (order + employees) for the same reason: the candidate-entry screen needs
 *  both the live price and the current draft rows in one round trip. */
@Data
public class HrOrderResponse {
    OrderResponse order;
    List<String> selectedAssessmentNames;
    List<HrCandidateDto> candidates;
}
