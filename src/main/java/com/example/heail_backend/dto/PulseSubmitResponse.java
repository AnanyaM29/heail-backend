package com.example.heail_backend.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PulseSubmitResponse {
    UUID sessionId;
    String pulseCode;
    String status;
    boolean allPulsesCompleted;
}
