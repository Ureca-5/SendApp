package com.mycom.myapp.sendapp.batch.service.settlement;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceDetailRowDto;

import java.util.List;

/**
 * 역할:
 * - 청크 단위(usersIds)에 대해 구독 원천 데이터를 "한 번에" 조회
 * - 디바이스 단위 세그먼트를 확정
 * - 구독 청구서 상세 DTO를 생성하여 반환
 *
 * 주의:
 * - invoiceId(FK)는 Writer에서 헤더 insert 후 주입되므로 여기서는 null로 둔다.
 * - 헤더 합계(totalPlanAmount 등) 갱신은 Writer에서 수행한다(현재 설계 기준).
 */
public interface SubscribeSettlementService {
    /**
     * @param targetYyyymm 정산 대상 월(YYYYMM)
     * @param usersIds     청크 단위 유저 목록(예: 1000명)
     * @return 구독 기반 청구서 상세 DTO 목록
     */
    List<MonthlyInvoiceDetailRowDto> settle(Integer targetYyyymm, List<Long> usersIds);
}
