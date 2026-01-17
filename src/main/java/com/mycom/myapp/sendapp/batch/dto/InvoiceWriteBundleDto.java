package com.mycom.myapp.sendapp.batch.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Processor -> Writer 전달용 내부 DTO.
 *
 * 유저 1명에 대한 정산 결과를 "쓰기 단위"로 묶는다.
 * - MonthlyInvoiceRowDto: 청구서 헤더 1건
 * - MonthlyInvoiceDetailRowDto: 청구서 상세 N건(구독은 세그먼트 단위로 여러 줄 가능)
 *
 * 설계 근거:
 * - Writer는 "헤더 upsert -> invoiceId 매핑 -> 상세 FK 세팅 -> 상세 batch insert"의 일관된 쓰기 흐름을 가져야 한다.
 * - Processor에서 계산된 결과를 하나의 번들로 묶어 전달하면, Writer의 책임이 "DB 반영"으로 명확히 분리된다.
 * - wrapper 타입 사용으로 null 허용/부재 상태를 명확히 표현한다(요구사항).
 */
@Getter
@Builder
public class InvoiceWriteBundleDto {
    /**
     * attempt_id (Step0에서 생성된 attempt PK)
     * - 배치 실행 단위 추적/로그/집계용
     * - 청크 단위 카운트 반영에도 사용 가능
     */
    private final Long attemptId;

    /**
     * 정산 대상 월 (YYYYMM)
     * - writer에서 검증/가드 로직에 활용 가능
     */
    private final Integer targetYyyymm;

    /**
     * 정산 대상 회원
     */
    private final Long usersId;

    /**
     * 헤더 1건 (upsert 대상)
     * - invoiceId는 upsert 전에는 null일 수 있음(UK: billing_yyyymm + users_id)
     */
    private final MonthlyInvoiceRowDto invoiceRowDto;

    /**
     * 상세 N건 (insert 대상)
     * - writer가 invoiceId 매핑 후 FK 세팅하여 insert한다.
     * - 각 detail은 (invoice_id + invoice_category_id + billing_history_id) UK로 멱등성을 확보한다.
     */
    private final List<MonthlyInvoiceDetailRowDto> detailRowDtoList;

    /**
     * (선택) processor가 판단한 "쓰기 생략" 플래그
     * - 예: 원천 데이터가 없거나, 모든 항목이 0원이라 정책상 저장하지 않는 경우 등
     * - 현재 정책이 없다면 null/false로 둔다.
     */
    private final Boolean skipWrite;

    /**
     * (선택) 이 번들에서 기대하는 상세 건수
     * - 검증/모니터링/로그 목적
     */
    private final Integer expectedDetailCount;

    /**
     * (선택) processor 내부 정산 중 발생한 경고/비정상 신호(예: 데이터 누락)
     * - 실패로 처리할지 여부는 writer/step 정책에 따라 결정
     */
    private final String warningMessage;
}
