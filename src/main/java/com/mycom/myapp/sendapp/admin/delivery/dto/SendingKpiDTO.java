package com.mycom.myapp.sendapp.admin.delivery.dto;

public record SendingKpiDTO(
        long targetCount,
        long readyCount,
        long sentCount,
        long failedCount
) {}
