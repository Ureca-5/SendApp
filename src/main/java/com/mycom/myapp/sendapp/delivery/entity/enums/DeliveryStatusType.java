package com.mycom.myapp.sendapp.delivery.entity.enums;

public enum DeliveryStatusType {
    READY,      // 준비
    PROCESSING, // 처리중
    SENT,       // 발송 완료 (최종 성공)
    FAILED;     // 발송 실패 (최종 실패)

    // DB 문자열 -> Enum 변환 (null이거나 모르는 값이면 null 반환)
    public static DeliveryStatusType from(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return DeliveryStatusType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null; // 혹은 예외를 던지거나 READY를 리턴하는 등 정책 결정
        }
    }
}
