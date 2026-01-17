package com.mycom.myapp.sendapp.admin.user.dto;

import java.time.LocalDateTime;

public record UserRowRawDTO(
        long usersId,
        String name,
        String emailEnc,
        String phoneEnc,
        LocalDateTime joinedAt,
        boolean withdrawn
) {}
