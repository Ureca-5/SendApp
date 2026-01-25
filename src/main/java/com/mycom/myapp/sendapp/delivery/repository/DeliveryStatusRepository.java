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

    // ... (기존 saveAll, updateStatusToProcessing 등은 유지) ...

    // [Loader용] 중복 무시 저장 (INSERT IGNORE)
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
                // 예약 시간이 있으면 넣고, 없으면 NULL
                ps.setTimestamp(7, status.getScheduledAt() != null ? Timestamp.valueOf(status.getScheduledAt()) : null);
            }
            @Override
            public int getBatchSize() {
                return statusList.size();
            }
        });
    }

    // ==========================================
    // ★ [추가] 야간 금지/예약 연기용 (Batch Update)
    // ==========================================
    public void postponeDelivery(List<Long> invoiceIds, LocalDateTime newScheduledAt) {
        if (invoiceIds == null || invoiceIds.isEmpty()) return;

        String sql = "UPDATE delivery_status " +
                     "SET status = 'SCHEDULED', scheduled_at = ?, last_attempt_at = NOW() " +
                     "WHERE invoice_id = ?";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ps.setTimestamp(1, Timestamp.valueOf(newScheduledAt));
                ps.setLong(2, invoiceIds.get(i));
            }
            @Override
            public int getBatchSize() {
                return invoiceIds.size();
            }
        });
    }
    
    // [Worker용] 최종 batch update
    public void updateStatusBatch(List<ProcessResult> results, LocalDateTime chunkNow) {
        if (results == null || results.isEmpty()) return;
        String sql = """
            UPDATE delivery_status 
               SET status = ?, 
                   last_attempt_at = ?
             WHERE invoice_id = ?
        """;
        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ProcessResult r = results.get(i);
                ps.setString(1, r.getStatus()); // SENT 또는 FAILED
                ps.setTimestamp(2, Timestamp.valueOf(chunkNow));
                ps.setLong(3, r.getInvoiceId());
            }
            @Override
            public int getBatchSize() { return results.size(); }
        });
    }
    
    // [Scheduler용] 재발송 대상 조회 (JOIN 쿼리)
    public List<DeliveryRetryDto> findRetryTargets(int maxRetry) {
        String sql = """
            SELECT 
                ds.invoice_id, 
                ds.delivery_channel, 
                ds.retry_count,
                mi.billing_yyyymm, 
                mi.total_amount,
                u.name AS recipient_name, 
                u.email AS email_info,
                u.phone AS phone_info
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id
            WHERE ds.status = 'FAILED' AND ds.retry_count < ?
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            return DeliveryRetryDto.builder()
                    .invoiceId(rs.getLong("invoice_id"))
                    .deliveryChannel(rs.getString("delivery_channel"))
                    .retryCount(rs.getInt("retry_count"))
                    .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                    .totalAmount(rs.getLong("total_amount"))
                    .recipientName(rs.getString("recipient_name"))
                    .email(rs.getString("email_info"))
                    .phone(rs.getString("phone_info"))
                    .build();
        }, maxRetry);
    }
    
    // [Scheduler용] 상태 초기화 (READY로 변경)
    public void resetStatusToReady(Long invoiceId, int currentRetryCount) {
        String sql = "UPDATE delivery_status " +
                     "SET status = 'READY', retry_count = ?, last_attempt_at = NOW() " +
                     "WHERE invoice_id = ?";
        jdbcTemplate.update(sql, currentRetryCount + 1, invoiceId);
    }

    // [Fallback용] 3회 실패한 이메일 -> SMS 전환 대상 조회
    public List<DeliveryRetryDto> findFallbackTargets(int maxRetry) {
        String sql = """
            SELECT 
                ds.invoice_id, 
                'SMS' as delivery_channel, 
                0 as retry_count,
                mi.billing_yyyymm, 
                mi.total_amount,
                u.name AS recipient_name, 
                u.email AS email_info,
                u.phone AS phone_info
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id
            WHERE ds.status = 'FAILED' 
              AND ds.retry_count >= ? 
              AND ds.delivery_channel = 'EMAIL'
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            return DeliveryRetryDto.builder()
                    .invoiceId(rs.getLong("invoice_id"))
                    .deliveryChannel("SMS")
                    .retryCount(0)
                    .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                    .totalAmount(rs.getLong("total_amount"))
                    .recipientName(rs.getString("recipient_name"))
                    .email(rs.getString("email_info"))
                    .phone(rs.getString("phone_info"))
                    .build();
        }, maxRetry);
    }

    // [Fallback용] DB 상태 변경 (EMAIL -> SMS, 카운트 초기화)
    public void switchToSms(Long invoiceId) {
        String sql = "UPDATE delivery_status " +
                     "SET status = 'READY', delivery_channel = 'SMS', retry_count = 0, last_attempt_at = NOW() " +
                     "WHERE invoice_id = ?";
        jdbcTemplate.update(sql, invoiceId);
    }

    // [Scheduler용] 예약 발송 대상 조회
    public List<DeliveryRetryDto> findScheduledTargets(LocalDateTime now) {
        String sql = """
            SELECT 
                ds.invoice_id,
                u.email, u.phone, u.name,
                mi.total_amount, mi.billing_yyyymm
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id
            WHERE ds.status = 'SCHEDULED' 
              AND ds.scheduled_at <= ?
        """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> DeliveryRetryDto.builder()
                .invoiceId(rs.getLong("invoice_id"))
                .deliveryChannel("EMAIL")
                .retryCount(0)
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .recipientName(rs.getString("name"))
                .totalAmount(rs.getLong("total_amount"))
                .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                .build()
        , Timestamp.valueOf(now));
    }
    
    // [Scheduler용] 상태 일괄 변경 (SCHEDULED -> READY)
    public void updateStatusToReadyBatch(List<Long> invoiceIds) {
        if (invoiceIds.isEmpty()) return;
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
}