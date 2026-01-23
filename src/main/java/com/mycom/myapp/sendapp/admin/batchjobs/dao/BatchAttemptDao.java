package com.mycom.myapp.sendapp.admin.batchjobs.dao;

import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowVM;
import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchJobStatusStatDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BatchAttemptDao {

    private final JdbcTemplate jdbcTemplate;

    public BatchAttemptDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BatchJobStatusStatDTO> statsByStatus(int yyyymm) {
        String sql = """
            SELECT execution_status AS status, COUNT(*) AS cnt
            FROM monthly_invoice_batch_attempt
            WHERE target_yyyymm = ?
            GROUP BY execution_status
            ORDER BY cnt DESC
        """;

        return jdbcTemplate.query(sql,
                (rs, n) -> new BatchJobStatusStatDTO(
                        rs.getString("status"),
                        rs.getLong("cnt")
                ),
                yyyymm
        );
    }

    public List<BatchAttemptRowVM> listRecentWithLastFail(int yyyymm, int limit) {
        int safeLimit = clamp(limit, 1, 200);

        // attempt별 최신 fail 1건 붙이기(created_at 기준)
        String sql = """
            SELECT
              a.attempt_id, a.target_yyyymm, a.execution_status, a.execution_type,
              a.started_at, a.ended_at, a.duration_ms,
              a.target_count, a.success_count, a.fail_count, a.host_name,
              lf.error_code AS last_error_code,
              lf.error_message AS last_error_message
            FROM monthly_invoice_batch_attempt a
            LEFT JOIN (
              SELECT f1.*
              FROM monthly_invoice_batch_fail f1
              JOIN (
                SELECT attempt_id, MAX(created_at) AS mx
                FROM monthly_invoice_batch_fail
                GROUP BY attempt_id
              ) t ON t.attempt_id = f1.attempt_id AND t.mx = f1.created_at
            ) lf ON lf.attempt_id = a.attempt_id
            WHERE a.target_yyyymm = ?
            ORDER BY a.started_at DESC, a.attempt_id DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, n) -> {
            var started = rs.getTimestamp("started_at");
            var ended = rs.getTimestamp("ended_at");

            var startedAt = (started == null) ? null : started.toLocalDateTime();
            var endedAt = (ended == null) ? null : ended.toLocalDateTime();

            Long durationMs = (rs.getObject("duration_ms") == null) ? null : rs.getLong("duration_ms");

            String code = rs.getString("last_error_code");
            String msg = rs.getString("last_error_message");
            String summary = (code == null && msg == null)
                    ? "-"
                    : (code == null ? "" : code + " - ") + trim(msg, 160);

            return new BatchAttemptRowVM(
                    rs.getLong("attempt_id"),
                    rs.getInt("target_yyyymm"),
                    rs.getString("execution_status"),
                    rs.getString("execution_type"),
                    startedAt,
                    endedAt,
                    durationMs,
                    rs.getLong("target_count"),
                    rs.getLong("success_count"),
                    rs.getLong("fail_count"),
                    rs.getString("host_name"),
                    summary
            );
        }, yyyymm, safeLimit);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    private static String trim(String s, int max) {
        if (s == null) return "-";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, max) + "…";
    }
}
