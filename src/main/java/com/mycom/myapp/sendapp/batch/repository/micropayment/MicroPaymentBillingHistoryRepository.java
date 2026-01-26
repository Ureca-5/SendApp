package com.mycom.myapp.sendapp.batch.repository.micropayment;

import com.mycom.myapp.sendapp.batch.dto.MicroPaymentBillingHistoryRowDto;

import java.util.List;

public interface MicroPaymentBillingHistoryRepository {
    /**
     * KeySet 페이징으로 단건 결제 원천 데이터를 조회합니다.
     *
     * @param targetYyyymm 정산월(YYYYMM)
     * @param usersIds     청크 유저 목록(최대 1000)
     * @param lastId       직전 조회의 마지막 PK (첫 호출은 0 또는 null)
     * @param limit        페이지 크기(예: 5000)
     */
    List<MicroPaymentBillingHistoryRowDto> findPageByUsersIdsAndYyyymmKeyset(
            Integer targetYyyymm,
            List<Long> usersIds,
            Long lastId,
            Integer limit
    );

    /**
     * PK 목록으로 단건 결제 원천 데이터를 조회합니다.
     */
    List<MicroPaymentBillingHistoryRowDto> findByIds(List<Long> billingHistoryIds);
}
