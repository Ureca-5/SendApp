package com.mycom.myapp.sendapp.admin.batchjobs.dao;

import com.mycom.myapp.sendapp.admin.batchjobs.dto.BatchFailRowVM;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class BatchjobsFailDao {

    private final JdbcTemplate jdbcTemplate;

    public BatchjobsFailDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BatchFailRowVM> listFailsByYyyymm(int yyyymm, Long attemptId, int limit) {
        int safeLimit = clamp(limit, 1, 500);

        // fail에는 target_yyyymm가 없어서 attempt JOIN으로 월 필터링
        String sql = """
            SELECT
              f.fail_id,
              f.attempt_id,
              a.target_yyyymm,
              f.created_at,
              f.invoice_category_id,
              f.billing_history_id,
              f.error_code,
              f.error_message
            FROM monthly_invoice_batch_fail f
            JOIN monthly_invoice_batch_attempt a ON a.attempt_id = f.attempt_id
            WHERE a.target_yyyymm = ?
              AND (? IS NULL OR f.attempt_id = ?)
            ORDER BY f.created_at DESC, f.fail_id DESC
            LIMIT ?
        """;

        return jdbcTemplate.query(sql, (rs, n) -> new BatchFailRowVM(
                rs.getLong("fail_id"),
                rs.getLong("attempt_id"),
                rs.getInt("target_yyyymm"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                (Integer) rs.getObject("invoice_category_id"),
                (Long) rs.getObject("billing_history_id"),
                rs.getString("error_code"),
                trim(rs.getString("error_message"), 240)
        ), yyyymm, attemptId, attemptId, safeLimit);
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
