package com.mycom.myapp.sendapp.batch.repository.attempt;

/**
 * 배치 실행 타입 enum
 */
public enum MonthlyInvoiceBatchExecutionType {
    SCHEDULED, // 정기 정산 배치
    MANUAL, // 수동 시작된 배치(관리자 기능)
    FORCE, // 배치 시작 선행 조건을 무시하고 강제 실행된 배치
    RETRY // 정산 실패분 재시도 배치
}
