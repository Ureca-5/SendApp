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

import com.mycom.myapp.sendapp.delivery.entity.DeliveryStatus;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryStatusType;

import lombok.RequiredArgsConstructor;

@Repository  
@RequiredArgsConstructor 
public class DeliveryStatusRepository {
	
	private final JdbcTemplate jdbcTemplate;
    private static final DeliveryStatusRowMapper ROW_MAPPER = new DeliveryStatusRowMapper();

    // ✅ Stash에서 가져온 핵심 로직 (이게 있어야 함!)
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
    
    // ✅ Stash에서 가져온 업데이트 로직
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