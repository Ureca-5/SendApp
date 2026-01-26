package com.mycom.myapp.sendapp.batch.repository.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;

import java.util.List;
import java.util.Map;

public interface MonthlyInvoiceRepository {
    /**
     * 청크 단위 헤더 신규 insert (batch) <br>
     * - UK(users_id, billing_yyyymm) 충돌 시 DuplicateKeyException 발생 가능 <br>
     * - "청구서 없는 유저만 Reader에서 뽑는다" 전제면 충돌은 드뭅니다. <br>
     * - 각 헤더의 insert 결과(영향받은 레코드 수) 배열 반환
     */
    int[] batchInsert(List<MonthlyInvoiceRowDto> headers);

    /**
     * 방금 insert된 헤더의 (users_id, invoice_id)를 일괄 조회합니다. <br>
     * - where users_id in (...) and billing_yyyymm = ? <br>
     * - 반환 row는 usersId, invoiceId만 채워서 반환
     */
    List<MonthlyInvoiceRowDto> findIdsByUsersIdsAndYyyymm(List<Long> usersIds, Integer billingYyyymm);

    /**
     * 청크 처리 후 totals / settlementSuccess / updatedAt 등을 일괄 update 합니다.
     * - invoice_id 기준 batch update
     */
    int[] batchUpdateTotals(List<MonthlyInvoiceRowDto> headers);

    /**
     * invoice_id 목록으로 월 청구서 헤더를 조회합니다.
     * - totals, users_id, billing_yyyymm 등을 포함
     */
    List<MonthlyInvoiceRowDto> findByInvoiceIds(List<Long> invoiceIds);
}
