package com.example.heail_backend.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class PulseStatusResponse {
    UUID orderId;
    List<PulseInfo> pulses;
    boolean allCompleted;
}
