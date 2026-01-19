package com.mycom.myapp.sendapp.admin.batchjobs.dao;

import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchAttemptRowDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BatchJobsDao {

    private final JdbcTemplate jdbcTemplate;

    public BatchJobsDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countAttempts(Integer billingYyyymm) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM monthly_invoice_batch_attempt
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        if (billingYyyymm != null) {
            sql.append(" AND target_yyyymm = ? ");
            args.add(billingYyyymm);
        }
        Integer cnt = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return cnt == null ? 0 : cnt;
    }

    public List<BatchAttemptRowDTO> listAttempts(Integer billingYyyymm, int size, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              attempt_id,
              target_yyyymm,
              execution_status,
              execution_type,
              started_at,
              ended_at,
              duration_ms,
              success_count,
              fail_count,
              host_name
            FROM monthly_invoice_batch_attempt
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        if (billingYyyymm != null) {
            sql.append(" AND target_yyyymm = ? ");
            args.add(billingYyyymm);
        }

        sql.append(" ORDER BY attempt_id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            LocalDateTime startedAt = toLdt(rs.getTimestamp("started_at"));
            LocalDateTime endedAt = toLdt(rs.getTimestamp("ended_at"));
            Long durationMs = rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms");

            return new BatchAttemptRowDTO(
                    rs.getLong("attempt_id"),
                    rs.getInt("target_yyyymm"),
                    rs.getString("execution_status"),
                    rs.getString("execution_type"),
                    startedAt,
                    endedAt,
                    durationMs,
                    rs.getLong("success_count"),
                    rs.getLong("fail_count"),
                    rs.getString("host_name")
            );
        }, args.toArray());
    }

    private static LocalDateTime toLdt(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
