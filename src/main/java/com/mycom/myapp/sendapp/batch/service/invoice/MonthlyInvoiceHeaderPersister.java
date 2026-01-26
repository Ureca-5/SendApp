package com.mycom.myapp.sendapp.batch.service.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;

import java.util.List;
import java.util.Map;

public interface MonthlyInvoiceHeaderPersister {
    /**
     * Header(월별 청구서) DTO 배치로 insert 수행
     */
    void batchInsertHeaders(List<MonthlyInvoiceRowDto> headers);

    /**
     * (users_id, billing_yyyymm) 기준으로 헤더의 (usersId, invoiceId)를 조회합니다.
     * - 반환 DTO에는 최소 usersId, invoiceId가 채워진다고 가정합니다.
     */
    List<MonthlyInvoiceRowDto> findIdsByUsersIdsAndYyyymm(List<Long> usersIds, Integer billingYyyymm);
}
