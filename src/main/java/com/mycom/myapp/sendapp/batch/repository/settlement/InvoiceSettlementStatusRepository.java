package com.mycom.myapp.sendapp.batch.repository.settlement;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceBatchFailRowDto;
import com.mycom.myapp.sendapp.batch.enums.SettlementStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface InvoiceSettlementStatusRepository {

    /**
     * 실패 원천 데이터들을 batch insert 합니다.
     *
     * @param rows fail row list (failId는 null)
     * @return 총 반영(삽입)된 row 수
     */
    int batchInsert(List<MonthlyInvoiceBatchFailRowDto> rows);
}
