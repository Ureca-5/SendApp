package com.mycom.myapp.sendapp.admin.user.dto;

import java.sql.Timestamp;
import java.time.LocalDateTime;

public record UserRowViewDTO(
        long usersId,
        String name,
        String emailMasked,
        String phoneMasked,
        LocalDateTime joinedAt,
        boolean withdrawn
) {}
