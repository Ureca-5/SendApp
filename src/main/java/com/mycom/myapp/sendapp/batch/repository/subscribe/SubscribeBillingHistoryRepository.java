package com.mycom.myapp.sendapp.batch.repository.subscribe;

import com.mycom.myapp.sendapp.batch.dto.SubscribeBillingHistoryRowDto;

import java.util.List;

public interface SubscribeBillingHistoryRepository {
    /**
     * 구독 원천 데이터(Subscribe Billing History)를 일괄 조회합니다.
     *
     * @param targetYyyymm 정산 대상 년월(YYYYMM)
     * @param usersIds     정산 대상 유저 식별자 리스트
     * @return 조회된 원천 데이터 리스트
     */
    List<SubscribeBillingHistoryRowDto> findByUsersIdsAndYyyymm(
            Integer targetYyyymm,
            List<Long> usersIds
    );
}
