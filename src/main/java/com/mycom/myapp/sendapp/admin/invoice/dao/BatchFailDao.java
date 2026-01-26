package com.mycom.myapp.sendapp.admin.invoice.dao;

import com.mycom.myapp.sendapp.admin.invoice.dto.BatchFailRowDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BatchFailDao {

    private final JdbcTemplate jdbcTemplate;

    public BatchFailDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int count(Integer targetYyyymm, Long attemptId, String errorCode) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM monthly_invoice_batch_fail f
            JOIN invoice_category ic ON f.invoice_category_id = ic.invoice_category_id
            JOIN monthly_invoice_batch_attempt a ON f.attempt_id = a.attempt_id
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, targetYyyymm, attemptId, errorCode);

        Integer cnt = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return cnt == null ? 0 : cnt;
    }

    public List<BatchFailRowDTO> list(Integer targetYyyymm, Long attemptId, String errorCode, int size, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              f.fail_id,
              f.attempt_id,
              f.invoice_category_id,
              ic.name AS invoice_category_name,
              f.billing_history_id,
              f.error_code,
              f.error_message,
              f.created_at
            FROM monthly_invoice_batch_fail f
            JOIN invoice_category ic ON f.invoice_category_id = ic.invoice_category_id
            JOIN monthly_invoice_batch_attempt a ON f.attempt_id = a.attempt_id
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, targetYyyymm, attemptId, errorCode);

        sql.append(" ORDER BY f.created_at DESC, f.fail_id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
            Timestamp created = rs.getTimestamp("created_at");
            LocalDateTime createdAt = created == null ? null : created.toLocalDateTime();

            return new BatchFailRowDTO(
                    rs.getLong("fail_id"),
                    rs.getLong("attempt_id"),
                    rs.getInt("invoice_category_id"),
                    rs.getString("invoice_category_name"),
                    rs.getLong("billing_history_id"),
                    rs.getString("error_code"),
                    rs.getString("error_message"),
                    createdAt
            );
        });
    }

    private static void applyWhere(StringBuilder sql, List<Object> args, Integer targetYyyymm, Long attemptId, String errorCode) {
        if (targetYyyymm != null) {
            sql.append(" AND a.target_yyyymm = ? ");
            args.add(targetYyyymm);
        }
        if (attemptId != null) {
            sql.append(" AND f.attempt_id = ? ");
            args.add(attemptId);
        }
        if (errorCode != null && !errorCode.isBlank()) {
            sql.append(" AND f.error_code = ? ");
            args.add(errorCode.trim());
        }
    }
}
