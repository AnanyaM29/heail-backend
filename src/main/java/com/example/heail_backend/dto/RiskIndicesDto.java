package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class RiskIndicesDto {
    Double trustDeficit;    // Management Integrity & Trust (S02), E-level only, 0-100
    Double executionDrag;   // GrowthPulse index - SystemPulse index, signed
    Double attritionSignal; // Attrition, Exit & Offboarding (S08), E-level only, 0-100
    Double changeFatigue;   // mean of Transformation Readiness (S20) + Culture/Fears (S16), E-level only, 0-100
}
