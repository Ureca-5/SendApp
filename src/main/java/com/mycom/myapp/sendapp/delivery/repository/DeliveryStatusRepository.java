package com.mycom.myapp.sendapp.delivery.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.mycom.myapp.sendapp.delivery.dto.DeliveryRetryDto;
import com.mycom.myapp.sendapp.delivery.dto.ProcessResult;
import com.mycom.myapp.sendapp.delivery.entity.DeliveryStatus;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryStatusType;

import lombok.RequiredArgsConstructor;

@Repository  
@RequiredArgsConstructor 
public class DeliveryStatusRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private static final DeliveryStatusRowMapper ROW_MAPPER = new DeliveryStatusRowMapper();

    // ==========================================
    // 1️⃣ [Loader용] 데이터 적재 (중복 무시)
    // ==========================================

    public void saveAllIgnore(List<DeliveryStatus> statusList) {
        String sql = "INSERT IGNORE INTO delivery_status " +
                     "(invoice_id, status, delivery_channel, retry_count, last_attempt_at, created_at, scheduled_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DeliveryStatus status = statusList.get(i);
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                
                ps.setLong(1, status.getInvoiceId());
                ps.setString(2, status.getStatus() != null ? status.getStatus().name() : DeliveryStatusType.READY.name());
                ps.setString(3, status.getDeliveryChannel().name());
                ps.setInt(4, 0); 
                ps.setTimestamp(5, now); 
                ps.setTimestamp(6, now);
                ps.setTimestamp(7, status.getScheduledAt() != null ? Timestamp.valueOf(status.getScheduledAt()) : null);
            }
            @Override
            public int getBatchSize() { return statusList.size(); }
        });
    }

    // ==========================================
    // 2️⃣ [Worker용] 상태 관리 및 결과 반영
    // ==========================================

    public boolean updateStatusToProcessing(Long id, String channel) {
        String sql = "UPDATE delivery_status SET status = 'PROCESSING', last_attempt_at = NOW() " +
                     "WHERE invoice_id = ? " +
                     "AND status IN ('READY', 'FAILED') " +
                     "AND delivery_channel = ?";
      
        int affectedRows = jdbcTemplate.update(sql, id, channel);
        return affectedRows == 1;
    }

    public void updateStatus(Long invoiceId, DeliveryStatusType newStatus) {
        String sql = "UPDATE delivery_status SET status = ?, last_attempt_at = NOW() WHERE invoice_id = ?";
        jdbcTemplate.update(sql, newStatus.name(), invoiceId);
    }

    public void updateStatusBatch(List<ProcessResult> results, LocalDateTime chunkNow) {
        if (results == null || results.isEmpty()) return;
        String sql = "UPDATE delivery_status SET status = ?, last_attempt_at = ? WHERE invoice_id = ?";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ProcessResult r = results.get(i);
                ps.setString(1, r.getStatus()); 
                ps.setTimestamp(2, Timestamp.valueOf(chunkNow));
                ps.setLong(3, r.getInvoiceId());
            }
            @Override
            public int getBatchSize() { return results.size(); }
        });
    }

    // ==========================================
    // 3️⃣ [Scheduler/Night Ban/Sync용] 조회 및 예약 로직
    // ==========================================

    public void postponeDelivery(List<Long> invoiceIds, LocalDateTime newScheduledAt) {
        if (invoiceIds == null || invoiceIds.isEmpty()) return;
        String sql = "UPDATE delivery_status SET status = 'SCHEDULED', scheduled_at = ?, last_attempt_at = NOW() WHERE invoice_id = ?";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setTimestamp(1, Timestamp.valueOf(newScheduledAt));
                ps.setLong(2, invoiceIds.get(i));
            }
            @Override
            public int getBatchSize() { return invoiceIds.size(); }
        });
    }

    /**
     * [재발송용] FAILED 상태 건 조회
     */
    public List<DeliveryRetryDto> findRetryTargets(int maxRetry) {
        String sql = """
            SELECT ds.invoice_id, ds.delivery_channel, ds.retry_count,
                   mi.billing_yyyymm, mi.total_amount, mi.due_date,
                   u.name AS recipient_name, u.email AS email_info, u.phone AS phone_info
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id
            WHERE ds.status = 'FAILED' AND ds.retry_count < ?
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> DeliveryRetryDto.builder()
                .invoiceId(rs.getLong("invoice_id"))
                .deliveryChannel(rs.getString("delivery_channel"))
                .retryCount(rs.getInt("retry_count"))
                .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                .totalAmount(rs.getLong("total_amount"))
                .recipientName(rs.getString("recipient_name"))
                .email(rs.getString("email_info"))
                .phone(rs.getString("phone_info"))
                .receiverInfo(rs.getString("email_info"))
                .dueDate(getLocalDateTime(rs, "due_date")) // 매핑 추가
                .build()
        , maxRetry);
    }
    
    public void resetStatusToReady(Long invoiceId, int currentRetryCount) {
        String sql = "UPDATE delivery_status SET status = 'READY', retry_count = ?, last_attempt_at = NOW() WHERE invoice_id = ?";
        jdbcTemplate.update(sql, currentRetryCount + 1, invoiceId);
    }

    /**
     * [Fallback용] 이메일 실패 -> SMS 전환 조회
     */
    public List<DeliveryRetryDto> findFallbackTargets(int maxRetry) {
        String sql = """
            SELECT ds.invoice_id, mi.billing_yyyymm, mi.total_amount, mi.due_date,
                   u.name AS recipient_name, u.email AS email_info, u.phone AS phone_info
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id
            WHERE ds.status = 'FAILED' AND ds.retry_count >= ? AND ds.delivery_channel = 'EMAIL'
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> DeliveryRetryDto.builder()
                .invoiceId(rs.getLong("invoice_id"))
                .deliveryChannel("SMS")
                .retryCount(0)
                .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                .totalAmount(rs.getLong("total_amount"))
                .recipientName(rs.getString("recipient_name"))
                .email(rs.getString("email_info"))
                .phone(rs.getString("phone_info"))
                .receiverInfo(rs.getString("phone_info"))
                .dueDate(getLocalDateTime(rs, "due_date")) // 매핑 추가
                .build()
        , maxRetry);
    }

    public void switchToSms(Long invoiceId) {
        String sql = "UPDATE delivery_status SET status = 'READY', delivery_channel = 'SMS', retry_count = 0, last_attempt_at = NOW() WHERE invoice_id = ?";
        jdbcTemplate.update(sql, invoiceId);
    }

    /**
     * [예약 발송용] SCHEDULED 시간 도래 건 조회
     */
    public List<DeliveryRetryDto> findScheduledTargets(LocalDateTime now) {
        String sql = """
            SELECT ds.invoice_id, mi.billing_yyyymm, mi.total_amount, mi.due_date,
                   u.name AS recipient_name, u.email AS email_info, u.phone AS phone_info
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id
            WHERE ds.status = 'SCHEDULED' AND ds.scheduled_at <= ?
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> DeliveryRetryDto.builder()
                .invoiceId(rs.getLong("invoice_id"))
                .deliveryChannel("EMAIL")
                .retryCount(0)
                .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                .totalAmount(rs.getLong("total_amount"))
                .recipientName(rs.getString("recipient_name"))
                .email(rs.getString("email_info"))
                .phone(rs.getString("phone_info"))
                .receiverInfo(rs.getString("email_info"))
                .dueDate(getLocalDateTime(rs, "due_date")) // 매핑 추가
                .build()
        , Timestamp.valueOf(now));
    }

    public void updateStatusToReadyBatch(List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) return;
        String sql = "UPDATE delivery_status SET status = 'READY', last_attempt_at = NOW() WHERE invoice_id = ?";
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setLong(1, invoiceIds.get(i));
            }
            @Override
            public int getBatchSize() { return invoiceIds.size(); }
        });
    }

    /**
     * [Sync용] 미아(Zombie) 상태 상세 조회
     */
    public List<DeliveryRetryDto> findZombieTargets(LocalDateTime thresholdTime) {
        String sql = """
            SELECT ds.invoice_id, ds.delivery_channel, ds.retry_count,
                   mi.billing_yyyymm, mi.total_amount, mi.due_date,
                   u.name AS recipient_name, u.email AS email_info, u.phone AS phone_info
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id
            WHERE (ds.status = 'READY' AND ds.created_at <= ?)
               OR (ds.status = 'PROCESSING' AND ds.last_attempt_at <= ?)
        """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> DeliveryRetryDto.builder()
                .invoiceId(rs.getLong("invoice_id"))
                .deliveryChannel(rs.getString("delivery_channel"))
                .retryCount(rs.getInt("retry_count"))
                .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                .totalAmount(rs.getLong("total_amount"))
                .recipientName(rs.getString("recipient_name"))
                .email(rs.getString("email_info"))
                .phone(rs.getString("phone_info"))
                .receiverInfo(rs.getString("email_info"))
                .dueDate(getLocalDateTime(rs, "due_date"))
                .build()
        , Timestamp.valueOf(thresholdTime), Timestamp.valueOf(thresholdTime));
    }

    // ==========================================
    // 4️⃣ 공통 Helper 및 RowMapper
    // ==========================================
    
    private static LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
        Timestamp ts = rs.getTimestamp(columnName);
        return (ts != null) ? ts.toLocalDateTime() : null;
    }

    private static final class DeliveryStatusRowMapper implements RowMapper<DeliveryStatus> {
        @Override
        public DeliveryStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DeliveryStatus.builder()
                    .deliveryStatusId(rs.getLong("delivery_status_id"))
                    .invoiceId(rs.getLong("invoice_id"))
                    .status(DeliveryStatusType.valueOf(rs.getString("status")))
                    .deliveryChannel(DeliveryChannelType.valueOf(rs.getString("delivery_channel")))
                    .retryCount(rs.getInt("retry_count"))
                    .lastAttemptAt(getLocalDateTime(rs, "last_attempt_at"))
                    .createdAt(getLocalDateTime(rs, "created_at"))
                    .scheduledAt(getLocalDateTime(rs, "scheduled_at"))
                    .build();
        }
    }
}