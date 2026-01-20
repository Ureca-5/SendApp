package com.mycom.myapp.sendapp.batch.repository.settlement;

import com.mycom.myapp.sendapp.batch.dto.SettlementStatusHistoryRowDto;

import java.util.List;

public interface InvoiceSettlementStatusHistoryRepository {
    /**
     * 정산 상태 이력 N건 BATCH INSERT
     * @return 각 row별 update count 배열
     */
    int[] batchInsert(List<SettlementStatusHistoryRowDto> rows);
}
