package com.mycom.myapp.sendapp.delivery.repository;

import com.mycom.myapp.sendapp.delivery.entity.DeliveryHistory;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryResultType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class DeliveryHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final DeliveryHistoryRowMapper ROW_MAPPER = new DeliveryHistoryRowMapper();

    // 발송 이력 저장
    public void save(DeliveryHistory history) {
        String sql = "INSERT INTO delivery_history " +
                     "(invoice_id, attempt_no, delivery_channel, receiver_info, status, error_message, requested_at, sent_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.update(sql,
                history.getInvoiceId(),
                history.getAttemptNo(),
                history.getDeliveryChannel().name(),
                history.getReceiverInfo(),
                history.getStatus().name(),
                history.getErrorMessage(),
                history.getRequestedAt() != null ? Timestamp.valueOf(history.getRequestedAt()) : null,
                history.getSentAt() != null ? Timestamp.valueOf(history.getSentAt()) : null
        );
    }
   
    
    
    private static final class DeliveryHistoryRowMapper implements RowMapper<DeliveryHistory> {
        @Override
        public DeliveryHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DeliveryHistory.builder()
                    .deliveryHistoryId(rs.getLong("delivery_history_id"))
                    .invoiceId(rs.getLong("invoice_id"))
                    .attemptNo(rs.getInt("attempt_no"))
                    // 직접 추출하여 변환 (불필요한 지역변수 생성 최소화)
                    .deliveryChannel(DeliveryChannelType.from(rs.getString("delivery_channel")))
                    .status(DeliveryResultType.from(rs.getString("status")))
                    .requestedAt(getLocalDateTime(rs, "requested_at"))
                    .sentAt(getLocalDateTime(rs, "sent_at"))
                    .build();
        }

        // 반복되는 Null 체크 및 변환 로직을 유틸리티화
        private LocalDateTime getLocalDateTime(ResultSet rs, String column) throws SQLException {
            Timestamp ts = rs.getTimestamp(column);
            return (ts != null) ? ts.toLocalDateTime() : null;
        }
    }
}