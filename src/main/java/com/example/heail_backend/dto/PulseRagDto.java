package com.example.heail_backend.dto;

import lombok.Data;

@Data
public class PulseRagDto {
    String pulseCode;
    String displayName;
    double index;
    String band;
    String rag; // derived display band — not the primary value, see index

    Double netGap;
    Double divergenceIndex;
    Double managementAlignment;
    Double consensus;
    Double polarisation;
    String warning;
}
