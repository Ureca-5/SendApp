package com.mycom.myapp.sendapp.batch.repository.settlement;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceBatchFailRowDto;

import java.util.List;

public interface InvoiceSettlementFailureRepository {
    /**
     * 실패 원천 데이터들을 batch insert 합니다.
     *
     * @param rows fail row list (failId는 null)
     * @return 총 반영(삽입)된 row 수
     */
    int[] batchInsert(List<MonthlyInvoiceBatchFailRowDto> rows);

    /**
     * invoice_id, invoice_category_id 조건으로 실패 원천 데이터를 조회합니다.
     */
    List<MonthlyInvoiceBatchFailRowDto> findByInvoiceIdsAndCategoryIds(List<Long> invoiceIds, List<Integer> categoryIds);

    /**
     * 단건 결제 카테고리 실패 원천 데이터를 fail_id 키셋으로 페이징 조회합니다.
     */
    List<MonthlyInvoiceBatchFailRowDto> findMicroByInvoiceIds(List<Long> invoiceIds, int microCategoryId, Long lastFailId, int limit);
}
