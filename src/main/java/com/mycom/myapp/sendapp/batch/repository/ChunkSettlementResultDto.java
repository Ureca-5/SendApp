package com.mycom.myapp.sendapp.batch.repository;

import lombok.Getter;
import lombok.Setter;

/**
 * 청크 내 정산 성공/실패 건수 기록용 dto
 */
@Getter
@Setter
public class ChunkSettlementResultDto {
    private Long successCount; // 이번 청크의 정산 성공 건수
    private Long failCount; // 이번 청크의 정산 실패 건수
}
