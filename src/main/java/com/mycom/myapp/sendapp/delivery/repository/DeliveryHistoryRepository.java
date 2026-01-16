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
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DeliveryHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final DeliveryHistoryRowMapper ROW_MAPPER = new DeliveryHistoryRowMapper();

    
   
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