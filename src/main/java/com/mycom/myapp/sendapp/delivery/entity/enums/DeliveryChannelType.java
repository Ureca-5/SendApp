package com.mycom.myapp.sendapp.delivery.entity.enums;

public enum DeliveryChannelType {
    EMAIL,
    SMS,
    PUSH;

    public static DeliveryChannelType from(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return DeliveryChannelType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; 
        }
    }
}