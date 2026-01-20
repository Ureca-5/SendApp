package com.mycom.myapp.sendapp.batch.repository.settlement;

import com.mycom.myapp.sendapp.batch.dto.SettlementStatusRowDto;

import java.util.List;
import java.util.Optional;

public interface InvoiceSettlementStatusRepository {

    /**
     * 청크 종료 시, invoice_id 기준으로 정산 상태 레코드를 "일괄 insert" 합니다.
     * - 이 배치는 "신규 insert"가 전제(= monthly_invoice를 새로 만든 청크)입니다.
     *
     * @return 실제 insert 처리 건수 합(성공한 row 수)
     */
    int[] batchInsert(List<SettlementStatusRowDto> rows);

    /**
     * 재정산/보정 시나리오 등을 위해 단건 조회가 필요하면 사용합니다.
     */
    Optional<SettlementStatusRowDto> findByInvoiceId(Long invoiceId);

    /**
     * 재정산 대상 선정 등을 위해 여러 건 조회가 필요하면 사용합니다.
     */
    List<SettlementStatusRowDto> findByInvoiceIds(List<Long> invoiceIds);
}
