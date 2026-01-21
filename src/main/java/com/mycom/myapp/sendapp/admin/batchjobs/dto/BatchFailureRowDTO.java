package com.mycom.myapp.sendapp.admin.batchjobs.dto;

public record BatchFailureRowDTO(
        Long usersId,
        Long invoiceId,
        String failureType,
        String message
) {}
