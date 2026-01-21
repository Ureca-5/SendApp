package com.mycom.myapp.sendapp.admin.batchjobs.dao;

import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowDTO;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchFailureRowDTO;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BatchJobsDao {

    private final JdbcTemplate jdbc;

    public BatchJobsDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // sending의 StatusCountRow 패턴을 그대로 사용
    public record StatusCountRow(String status, long count) {}

    public List<StatusCountRow> statusStats(int billingYyyymm) {
        String sql = """
            SELECT status, COUNT(*) AS cnt
            FROM monthly_invoice_batch_attempt
            WHERE target_yyyymm = ?
            GROUP BY status
            ORDER BY status
        """;
        return jdbc.query(sql, (rs, i) ->
                new StatusCountRow(rs.getString("status"), rs.getLong("cnt")),
                billingYyyymm
        );
    }

    public List<BatchAttemptRowDTO> listAttemptsRecent(int billingYyyymm, int limit) {
        String sql = """
            SELECT
                attempt_id,
                target_yyyymm,
                status,
                started_at,
                finished_at,
                target_user_count,
                processed_user_count,
                fail_count,
                host_name,
                error_message
            FROM monthly_invoice_batch_attempt
            WHERE target_yyyymm = ?
            ORDER BY attempt_id DESC
            LIMIT ?
        """;
        return jdbc.query(sql, (rs, i) -> mapAttempt(rs), billingYyyymm, limit);
    }

    public BatchAttemptRowDTO readAttempt(long attemptId) {
        String sql = """
            SELECT
                attempt_id,
                target_yyyymm,
                status,
                started_at,
                finished_at,
                target_user_count,
                processed_user_count,
                fail_count,
                host_name,
                error_message
            FROM monthly_invoice_batch_attempt
            WHERE attempt_id = ?
        """;
        List<BatchAttemptRowDTO> rows = jdbc.query(sql, (rs, i) -> mapAttempt(rs), attemptId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * ⚠️ 실패 목록 테이블이 확정되기 전까지는 empty 반환.
     * (예: monthly_invoice_settlement_failure 같은 테이블이 확정되면 여기 구현)
     */
    public List<BatchFailureRowDTO> listFailures(long attemptId, int limit) {
        return List.of();
    }

    private BatchAttemptRowDTO mapAttempt(ResultSet rs) throws java.sql.SQLException {
        return new BatchAttemptRowDTO(
                rs.getLong("attempt_id"),
                rs.getInt("target_yyyymm"),
                rs.getString("status"),
                toLdt(rs.getTimestamp("started_at")),
                toLdt(rs.getTimestamp("finished_at")),
                rs.getInt("target_user_count"),
                rs.getInt("processed_user_count"),
                rs.getInt("fail_count"),
                rs.getString("host_name"),
                rs.getString("error_message")
        );
    }

    private LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
