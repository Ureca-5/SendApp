package com.mycom.myapp.sendapp.batch.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MonthlyInvoiceBatchAttemptJdbcRepository implements MonthlyInvoiceBatchAttemptRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean existsStartedOrCompleted(int targetYyyymm) {
        // STARTED/COMPLETED가 동시에 존재하면 안 된다는 정책(실행 가능 조건)을 위해 필요
        String sql = """
                SELECT EXISTS(
                    SELECT 1
                    FROM monthly_invoice_batch_attempt
                    WHERE target_yyyymm = ?
                      AND execution_status IN (?, ?)
                    LIMIT 1
                )
                """;

        Boolean exists = jdbcTemplate.queryForObject(
                sql,
                Boolean.class,
                targetYyyymm,
                MonthlyInvoiceBatchExecutionStatus.STARTED.name(),
                MonthlyInvoiceBatchExecutionStatus.COMPLETED.name()
        );

        return Boolean.TRUE.equals(exists);
    }

    @Override
    public long insertStartedAttempt(
            int targetYyyymm,
            long targetCount,
            MonthlyInvoiceBatchExecutionType executionType,
            String hostName,
            LocalDateTime startedAt
    ) {
        // 상태는 항상 STARTED로 고정
        String sql = """
                INSERT INTO monthly_invoice_batch_attempt
                (target_yyyymm, execution_status, execution_type, started_at,
                 success_count, fail_count, host_name)
                VALUES (?, ?, ?, ?, 0, 0, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        int updated = jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, targetYyyymm);
            ps.setString(2, MonthlyInvoiceBatchExecutionStatus.STARTED.name());
            ps.setString(3, executionType.name());
            ps.setObject(4, startedAt);
            ps.setString(5, hostName);
            return ps;
        }, keyHolder);

        if (updated != 1) {
            throw new IllegalStateException("attempt insert 실패: updatedRows=" + updated);
        }

        Number key = keyHolder.getKey();
        if (key == null) {
            // 여기로 떨어진다면 attempt_id가 AUTO_INCREMENT가 아닌 경우가 대부분입니다.
            throw new IllegalStateException("생성된 attempt_id를 가져오지 못했습니다. attempt_id AUTO_INCREMENT 설정을 확인해주세요.");
        }
        return key.longValue();
    }

    @Override
    public int markCompleted(long attemptId, LocalDateTime endedAt, long durationMs) {
        return markFinished(attemptId, MonthlyInvoiceBatchExecutionStatus.COMPLETED, endedAt, durationMs);
    }

    @Override
    public int markFailed(long attemptId, LocalDateTime endedAt, long durationMs) {
        return markFinished(attemptId, MonthlyInvoiceBatchExecutionStatus.FAILED, endedAt, durationMs);
    }

    private int markFinished(
            long attemptId,
            MonthlyInvoiceBatchExecutionStatus finishStatus,
            LocalDateTime endedAt,
            long durationMs
    ) {
        // STARTED인 attempt만 종료 처리 (이미 종료된 attempt를 다시 바꾸지 않음)
        String sql = """
                UPDATE monthly_invoice_batch_attempt
                   SET execution_status = ?,
                       ended_at = ?,
                       duration_ms = ?
                 WHERE attempt_id = ?
                   AND execution_status = ?
                """;

        return jdbcTemplate.update(
                sql,
                finishStatus.name(),
                endedAt,
                durationMs,
                attemptId,
                MonthlyInvoiceBatchExecutionStatus.STARTED.name()
        );
    }

    @Override
    public int applyChunkResult(
            Long attemptId,
            ChunkSettlementResultDto chunkResult
    ) {
        String sql = """
                UPDATE monthly_invoice_batch_attempt
                SET success_count = success_count + ?, fail_count = fail_count + ?
                WHERE attempt_id = ?
                """;
        return jdbcTemplate.update(
                sql,
                chunkResult.getSuccessCount(),
                chunkResult.getFailCount(),
                attemptId
        );
    }

    @Override
    public Optional<MonthlyInvoiceBatchAttemptDto> findById(long attemptId) {
        String sql = """
                SELECT attempt_id, target_yyyymm, execution_status, execution_type,
                       started_at, ended_at, duration_ms,
                       success_count, fail_count, host_name
                  FROM monthly_invoice_batch_attempt
                 WHERE attempt_id = ?
                """;

        try {
            MonthlyInvoiceBatchAttemptDto dto = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                            MonthlyInvoiceBatchAttemptDto.builder()
                                    .attemptId(rs.getLong("attempt_id"))
                                    .targetYyyymm(rs.getInt("target_yyyymm"))
                                    .executionStatus(MonthlyInvoiceBatchExecutionStatus.valueOf(rs.getString("execution_status")))
                                    .executionType(MonthlyInvoiceBatchExecutionType.valueOf(rs.getString("execution_type")))
                                    .startedAt(rs.getTimestamp("started_at").toLocalDateTime())
                                    .endedAt(rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toLocalDateTime())
                                    .durationMs(rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms"))
                                    .successCount(rs.getLong("success_count"))
                                    .failCount(rs.getLong("fail_count"))
                                    .hostName(rs.getString("host_name"))
                                    .build()
                    , attemptId);

            return Optional.ofNullable(dto);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
