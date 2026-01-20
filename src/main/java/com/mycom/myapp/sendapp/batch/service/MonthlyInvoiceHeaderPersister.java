package com.mycom.myapp.sendapp.batch.service;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;

import java.util.List;
import java.util.Map;

public interface MonthlyInvoiceHeaderPersister {
    /**
     * Header(월별 청구서) DTO 배치로 insert 수행
     */
    void batchInsertHeaders(List<MonthlyInvoiceRowDto> headers);

    /**
     * Insert 완료 후, DB에서 usersId 기준 invoiceId를 조회하여 반환
     *
     * @param targetYyyymm 정산 년월
     * @param usersIds     청크 내의 유저 식별자 리스트
     * @return usersId -> invoiceId 맵
     */
    Map<Long, Long> findInvoiceIdsByUsers(Integer targetYyyymm, List<Long> usersIds);
}
