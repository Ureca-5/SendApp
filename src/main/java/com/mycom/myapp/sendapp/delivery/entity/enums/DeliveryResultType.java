package com.mycom.myapp.sendapp.delivery.entity.enums;

public enum DeliveryResultType {
    SUCCESS,
    FAIL;

    public static DeliveryResultType from(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return DeliveryResultType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}