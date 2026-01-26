package com.mycom.myapp.sendapp.batch.repository.attempt;

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

    /**
     * 지정 시각 이전에 STARTED 상태로 남아있는 가장 오래된 attempt를 조회합니다.
     */
    Optional<MonthlyInvoiceBatchAttemptDto> findOldestStartedBefore(LocalDateTime cutoff);

    /**
     * 지정 시각 이후에 STARTED 상태 attempt가 존재하는지 확인합니다.
     */
    boolean existsStartedAfter(LocalDateTime cutoff);

    /**
     * STARTED → INTERRUPTED 로 상태 전환하면서 종료 시각/소요 시간을 기록합니다.
     */
    int markInterrupted(long attemptId, LocalDateTime endedAt, long durationMs);

    /**
     * FORCE 타입의 STARTED attempt를 생성합니다.
     * - target_count/success_count/fail_count를 호출자가 지정한 값으로 채웁니다.
     */
    long insertForceStartedAttempt(int targetYyyymm,
                                   long targetCount,
                                   long successCount,
                                   long failCount,
                                   String hostName,
                                   LocalDateTime startedAt);
}
