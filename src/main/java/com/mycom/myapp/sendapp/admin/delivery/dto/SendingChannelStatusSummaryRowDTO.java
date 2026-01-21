package com.mycom.myapp.sendapp.admin.delivery.dto;

public record SendingChannelStatusSummaryRowDTO(
        String channel,
        String status,
        long cnt
) {}
