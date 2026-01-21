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
        String sql = "INSERT IGNORE INTO delivery_status " +
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
    
    
    // [WORKER용] 최종 batch update
    public void updateStatusBatch(List<ProcessResult> results, LocalDateTime chunkNow) {
        if (results == null || results.isEmpty()) return;

        // WHERE 조건에 채널을 포함하여 정확한 레코드 타겟팅
        String sql = """
            UPDATE delivery_status 
               SET status = ?, 
                   retry_count = ?, 
                   last_attempt_at = ?
             WHERE invoice_id = ?
        """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ProcessResult r = results.get(i);
                
                ps.setString(1, r.getStatus()); // SENT 또는 FAILED
                ps.setInt(2, r.getAttemptNo()); 
                ps.setTimestamp(3, Timestamp.valueOf(chunkNow));
                ps.setLong(4, r.getInvoiceId());
            }

            @Override
            public int getBatchSize() {
                return results.size();
            }
        });
    // ==========================================
    // 5️⃣ [Scheduler용] 재발송 대상 조회 (JOIN 쿼리)
    // ==========================================
    public List<DeliveryRetryDto> findRetryTargets(int maxRetry) {
        // ★ 중요: 실제 DB 스키마(users)에 맞춰 컬럼명 수정됨
        // phone_no (X) -> phone (O)
        // users_id (O)
        String sql = """
            SELECT 
                ds.invoice_id, 
                ds.delivery_channel, 
                ds.retry_count,
                mi.billing_yyyymm, 
                mi.total_amount,
                u.name AS recipient_name, 
                
                -- ★ 채널이 이메일이면 email, 문자면 phone을 가져오도록 분기 처리 (선택사항)
                -- 일단은 email을 가져오게 둠. (필요시 u.phone 으로 변경)
                u.email AS receiver_info
                
            FROM delivery_status ds
            INNER JOIN monthly_invoice mi ON ds.invoice_id = mi.invoice_id
            INNER JOIN users u ON mi.users_id = u.users_id  -- ★ delivery_user -> users
            WHERE ds.status = 'FAILED' 
              AND ds.retry_count < ?
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