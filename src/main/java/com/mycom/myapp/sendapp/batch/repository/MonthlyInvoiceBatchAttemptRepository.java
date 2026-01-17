package com.mycom.myapp.sendapp.batch.repository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MonthlyInvoiceBatchAttemptRepository {
    /**
     * (Step0) 특정 target_yyyymm에 대해 STARTED 또는 COMPLETED attempt가 존재하는지 검사
     * - 단일 서버만 가정해도 운영 안전상 존재 여부 검사는 필요합니다.
     */
    boolean existsStartedOrCompleted(int targetYyyymm);

    /**
     * attempt 생성 (execution_status는 항상 STARTED로 저장)
     * @return 생성된 attempt_id
     */
    long insertStartedAttempt(
            int targetYyyymm,
            long targetCount,
            MonthlyInvoiceBatchExecutionType executionType,
            String hostName,
            LocalDateTime startedAt
    );

    /**
     * 성공 종료 기록 (status는 COMPLETED로 고정)
     * - STARTED인 경우에만 종료 처리
     * @return 영향받은 row 수(정상: 1, 이미 종료됨/없음: 0)
     */
    int markCompleted(long attemptId, LocalDateTime endedAt, long durationMs);

    /**
     * 실패 종료 기록 (status는 FAILED로 고정)
     * - STARTED인 경우에만 종료 처리
     * @return 영향받은 row 수(정상: 1, 이미 종료됨/없음: 0)
     */
    int markFailed(long attemptId, LocalDateTime endedAt, long durationMs);

    /**
     * 청크의 정산 성공/실패 건수 일괄 반영
     * @param attemptId 배치 시도 기록 레코드 식별자
     * @param chunkResult 정산 성공/실패 건수 기록한 dto
     * @return
     */
    int applyChunkResult(
            Long attemptId,
            ChunkSettlementResultDto chunkResult
    );

    /**
     * attempt 조회(필요 시)
     */
    Optional<MonthlyInvoiceBatchAttemptDto> findById(long attemptId);
}
