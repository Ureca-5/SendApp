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
    // 1️⃣ [Loader용] 대량 적재 기능 (왼쪽 코드)
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
    // 2️⃣ [Worker용] 중복 방지 및 상태 선점 (오른쪽 코드)
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
    // 3️⃣ [Worker용] 발송 결과 업데이트 (오른쪽 코드)
    // ==========================================
    public void updateResult(Long id, DeliveryStatusType status, LocalDateTime lastAttemptAt) {
        String sql = "UPDATE delivery_status SET status = ?, last_attempt_at = ? " +
                     "WHERE invoice_id = ?";
        
        jdbcTemplate.update(sql, status.name(), Timestamp.valueOf(lastAttemptAt), id);
    }

    
    // ==========================================
    // 4️⃣ [기타] 단순 상태 업데이트 (왼쪽 코드 - 필요시 사용)
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
    //중복키 에러시 에러난 지점 스킵후 나머지 저장하는 코드, 기존 saveAll 사용 안하고 이 함수 사용
    public void saveAllIgnore(List<DeliveryStatus> statusList) {
        // ⚠️ MySQL 전용 문법: INSERT IGNORE
        // 중복된 PK(invoice_id)가 들어오면 에러 없이 무시하고 넘어감
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
    }
    
    /**
     * 내부 정적 RowMapper 클래스 (공통)
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