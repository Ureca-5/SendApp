//package com.mycom.myapp.sendapp.admin.invoice.dao;
//
//import com.mycom.myapp.sendapp.admin.invoice.dto.BatchAttemptRowDTO;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Repository;
//
//import java.sql.Timestamp;
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//@Repository
//public class BatchAttemptDao {
//
//    private final JdbcTemplate jdbcTemplate;
//
//    public BatchAttemptDao(JdbcTemplate jdbcTemplate) {
//        this.jdbcTemplate = jdbcTemplate;
//    }
//
//    public int count(Integer targetYyyymm, String executionStatus) {
//        StringBuilder sql = new StringBuilder("""
//            SELECT COUNT(*)
//            FROM monthly_invoice_batch_attempt a
//            WHERE 1=1
//        """);
//        List<Object> args = new ArrayList<>();
//        applyWhere(sql, args, targetYyyymm, executionStatus);
//
//        Integer cnt = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
//        return cnt == null ? 0 : cnt;
//    }
//
//    public List<BatchAttemptRowDTO> list(Integer targetYyyymm, String executionStatus, int size, int offset) {
//        StringBuilder sql = new StringBuilder("""
//            SELECT
//              a.attempt_id,
//              a.target_yyyymm,
//              a.execution_status,
//              a.execution_type,
//              a.started_at,
//              a.ended_at,
//              a.duration_ms,
//              a.target_count,
//              a.success_count,
//              a.fail_count,
//              a.host_name
//            FROM monthly_invoice_batch_attempt a
//            WHERE 1=1
//        """);
//        List<Object> args = new ArrayList<>();
//        applyWhere(sql, args, targetYyyymm, executionStatus);
//
//        sql.append(" ORDER BY a.started_at DESC, a.attempt_id DESC LIMIT ? OFFSET ? ");
//        args.add(size);
//        args.add(offset);
//
//        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
//            Timestamp started = rs.getTimestamp("started_at");
//            Timestamp ended = rs.getTimestamp("ended_at");
//            LocalDateTime startedAt = started == null ? null : started.toLocalDateTime();
//            LocalDateTime endedAt = ended == null ? null : ended.toLocalDateTime();
//
//            Long durationMs = rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms");
//
//            return new BatchAttemptRowDTO(
//                    rs.getLong("attempt_id"),
//                    rs.getInt("target_yyyymm"),
//                    rs.getString("execution_status"),
//                    rs.getString("execution_type"),
//                    startedAt,
//                    endedAt,
//                    durationMs,
//                    rs.getLong("target_count"),
//                    rs.getLong("success_count"),
//                    rs.getLong("fail_count"),
//                    rs.getString("host_name")
//            );
//        });
//    }
//
//    public BatchAttemptRowDTO findOne(long attemptId) {
//        String sql = """
//            SELECT
//              a.attempt_id,
//              a.target_yyyymm,
//              a.execution_status,
//              a.execution_type,
//              a.started_at,
//              a.ended_at,
//              a.duration_ms,
//              a.target_count,
//              a.success_count,
//              a.fail_count,
//              a.host_name
//            FROM monthly_invoice_batch_attempt a
//            WHERE a.attempt_id = ?
//        """;
//
//        return jdbcTemplate.query(sql, rs -> {
//            if (!rs.next()) return null;
//            Timestamp started = rs.getTimestamp("started_at");
//            Timestamp ended = rs.getTimestamp("ended_at");
//            LocalDateTime startedAt = started == null ? null : started.toLocalDateTime();
//            LocalDateTime endedAt = ended == null ? null : ended.toLocalDateTime();
//            Long durationMs = rs.getObject("duration_ms") == null ? null : rs.getLong("duration_ms");
//
//            return new BatchAttemptRowDTO(
//                    rs.getLong("attempt_id"),
//                    rs.getInt("target_yyyymm"),
//                    rs.getString("execution_status"),
//                    rs.getString("execution_type"),
//                    startedAt,
//                    endedAt,
//                    durationMs,
//                    rs.getLong("target_count"),
//                    rs.getLong("success_count"),
//                    rs.getLong("fail_count"),
//                    rs.getString("host_name")
//            );
//        }, attemptId);
//    }
//
//    private static void applyWhere(StringBuilder sql, List<Object> args, Integer targetYyyymm, String executionStatus) {
//        if (targetYyyymm != null) {
//            sql.append(" AND a.target_yyyymm = ? ");
//            args.add(targetYyyymm);
//        }
//        if (executionStatus != null && !executionStatus.isBlank()) {
//            sql.append(" AND a.execution_status = ? ");
//            args.add(executionStatus.trim());
//        }
//    }
//}
