package com.example.heail_backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrgOrderResponse {
    OrderResponse order;
    List<OrderEmployeeDto> employees;
}
