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

import com.mycom.myapp.sendapp.delivery.dto.ProcessResult;
import com.mycom.myapp.sendapp.delivery.dto.DeliveryRetryDto;
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
    // 1️⃣ [Loader용] 대량 적재 기능
    // ==========================================
    public void saveAll(List<DeliveryStatus> statusList) {
        String sql = "INSERT INTO delivery_status " +
                     "(invoice_id, status, delivery_channel, retry_count, last_attempt_at, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

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
            }

            @Override
            public int getBatchSize() {
                return statusList.size();
            }
        });
    }

    // ==========================================
    // 2️⃣ [Worker용] 중복 방지 및 상태 선점
    // ==========================================
    public boolean updateStatusToProcessing(Long id, String channel) {
        String sql = "UPDATE delivery_status SET status = 'PROCESSING', last_attempt_at = NOW() " +
                     "WHERE invoice_id = ? " +
                     "AND status IN ('READY', 'FAILED') " +
                     "AND delivery_channel = ?";
        
        int affectedRows = jdbcTemplate.update(sql, id, channel);
        return affectedRows == 1;
    }
    
    // ==========================================
    // 3️⃣ [Worker용] 발송 결과 업데이트
    // ==========================================
    public void updateResult(Long id, DeliveryStatusType status, LocalDateTime lastAttemptAt) {
        String sql = "UPDATE delivery_status SET status = ?, last_attempt_at = ? " +
                     "WHERE invoice_id = ?";
        
        jdbcTemplate.update(sql, status.name(), Timestamp.valueOf(lastAttemptAt), id);
    }

    
    // ==========================================
    // 4️⃣ [기타] 단순 상태 업데이트
    // ==========================================
    public void updateStatus(Long invoiceId, DeliveryStatusType newStatus) {
        String sql = "UPDATE delivery_status " +
                     "SET status = ?, last_attempt_at = ? " +
                     "WHERE invoice_id = ?";

        jdbcTemplate.update(sql,
                newStatus.name(),
                Timestamp.valueOf(LocalDateTime.now()),
                invoiceId
        );
    }

    // [Loader용] 중복 무시 저장 (INSERT IGNORE)
    public void saveAllIgnore(List<DeliveryStatus> statusList) {
        // ★ scheduled_at 컬럼 추가
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
                // ★ 예약 시간이 있으면 넣고, 없으면 NULL
                ps.setTimestamp(7, status.getScheduledAt() != null ? Timestamp.valueOf(status.getScheduledAt()) : null);
            }
            @Override
            public int getBatchSize() {
                return statusList.size();
            }
        });
    }
    
    
    // [WORKER용] 최종 batch update
    public void updateStatusBatch(List<ProcessResult> results, LocalDateTime chunkNow) {
        if (results == null || results.isEmpty()) return;

        // WHERE 조건에 채널을 포함하여 정확한 레코드 타겟팅
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
            public int getBatchSize() {
                return results.size();
            }
        });
    }
    
    // ==========================================
    // 5️⃣ [Scheduler용] 재발송 대상 조회 (JOIN 쿼리)
    // ==========================================
    public List<DeliveryRetryDto> findRetryTargets(int maxRetry) {
        String sql = """
            SELECT 
                ds.invoice_id, 
                ds.delivery_channel, 
                ds.retry_count,
                mi.billing_yyyymm, 
                mi.total_amount,
                u.name AS recipient_name, 
                
                -- ★ [수정] 이메일, 폰 둘 다 조회
                u.email AS email_info,
                u.phone AS phone_info,
                
                u.email AS receiver_info -- 이번 발송 타겟
                
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
                    .receiverInfo(rs.getString("receiver_info"))
                    // ★ [추가] 둘 다 담기
                    .email(rs.getString("email_info"))
                    .phone(rs.getString("phone_info"))
                    .build();
        }, maxRetry);
    }
    
    // ==========================================
    // 6️⃣ [Scheduler용] 상태 초기화
    // ==========================================
    public void resetStatusToReady(Long invoiceId, int currentRetryCount) {
        String sql = "UPDATE delivery_status " +
                     "SET status = 'READY', retry_count = ?, last_attempt_at = ? " +
                     "WHERE invoice_id = ?";
        
        jdbcTemplate.update(sql, 
            currentRetryCount + 1,        
            Timestamp.valueOf(LocalDateTime.now()), 
            invoiceId
        );
    }
    // ==========================================
    // 7️⃣ [Fallback용] 3회 실패한 이메일 -> SMS 전환 대상 조회
    // ==========================================
    public List<DeliveryRetryDto> findFallbackTargets(int maxRetry) {
        String sql = """
            SELECT 
                ds.invoice_id, 
                'SMS' as delivery_channel, 
                0 as retry_count,
                mi.billing_yyyymm, 
                mi.total_amount,
                u.name AS recipient_name, 
                
                -- ★ [수정] 이메일, 폰 둘 다 조회
                u.email AS email_info,
                u.phone AS phone_info,
                
                u.phone AS receiver_info -- 이번 발송 타겟(SMS)
                
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
                    .receiverInfo(rs.getString("receiver_info"))
                    // ★ [추가] 둘 다 담기
                    .email(rs.getString("email_info"))
                    .phone(rs.getString("phone_info"))
                    .build();
        }, maxRetry);
    }
    // ==========================================
    // 8️⃣ [Fallback용] DB 상태 변경 (EMAIL -> SMS, 카운트 초기화)
    // ==========================================
    public void switchToSms(Long invoiceId) {
        String sql = "UPDATE delivery_status " +
                     "SET status = 'READY', " +     // 다시 대기 상태로
                     "    delivery_channel = 'SMS', " + // 채널 변경
                     "    retry_count = 0, " +      // 횟수 리셋 (SMS로서 새출발)
                     "    last_attempt_at = ? " +
                     "WHERE invoice_id = ?";
        
        jdbcTemplate.update(sql, 
            Timestamp.valueOf(LocalDateTime.now()), 
            invoiceId
        );
    }
    // ==========================================
    // [Scheduler용] 예약 발송 대상 조회
    // ==========================================
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
        
        // DeliveryRetryDto를 재활용하여 매핑
        return jdbcTemplate.query(sql, (rs, rowNum) -> DeliveryRetryDto.builder()
                .invoiceId(rs.getLong("invoice_id"))
                .deliveryChannel("EMAIL") // 예약은 기본 EMAIL로 가정
                .retryCount(0)            // 첫 시도
                .email(rs.getString("email"))
                .phone(rs.getString("phone"))
                .recipientName(rs.getString("name"))
                .totalAmount(rs.getLong("total_amount"))
                .billingYyyymm(String.valueOf(rs.getInt("billing_yyyymm")))
                .receiverInfo(rs.getString("email")) // 기본 수신 정보
                .build()
        , Timestamp.valueOf(now));
    }
    
    // ==========================================
    // [Scheduler용] 상태 일괄 변경 (SCHEDULED -> READY)
    // ==========================================
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
    /**
     * 내부 정적 RowMapper 클래스
     */
    private static final class DeliveryStatusRowMapper implements RowMapper<DeliveryStatus> {
        @Override
        public DeliveryStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DeliveryStatus.builder()
                    .deliveryStatusId(rs.getLong("delivery_status_id"))
                    .invoiceId(rs.getLong("invoice_id"))
                    .status(DeliveryStatusType.from(rs.getString("status")))
                    .deliveryChannel(DeliveryChannelType.from(rs.getString("delivery_channel")))
                    .retryCount(rs.getInt("retry_count"))
                    .lastAttemptAt(getLocalDateTime(rs, "last_attempt_at"))
                    .createdAt(getLocalDateTime(rs, "created_at"))
                    .build();
        }

        private LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
            Timestamp ts = rs.getTimestamp(columnName);
            return (ts != null) ? ts.toLocalDateTime() : null;
        }
    } 
}