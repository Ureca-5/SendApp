package com.mycom.myapp.sendapp.admin.user.dto;

import java.time.LocalDateTime;

public record UserRowViewDTO(
        long usersId,
        String name,
        String email,   // 평문
        String phone,   // 평문
        LocalDateTime joinedAt,
        boolean withdrawn
) {}
