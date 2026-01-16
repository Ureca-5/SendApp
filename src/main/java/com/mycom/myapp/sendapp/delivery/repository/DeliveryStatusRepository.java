package com.mycom.myapp.sendapp.delivery.repository;

import com.mycom.myapp.sendapp.delivery.entity.DeliveryStatus;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryStatusType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository  
@RequiredArgsConstructor 
public class DeliveryStatusRepository {
	
	private final JdbcTemplate jdbcTemplate;
    private static final DeliveryStatusRowMapper ROW_MAPPER = new DeliveryStatusRowMapper();

    /**
     * 내부 정적 RowMapper 클래스
     */
    private static final class DeliveryStatusRowMapper implements RowMapper<DeliveryStatus> {
        @Override
        public DeliveryStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DeliveryStatus.builder()
                    .deliveryStatusId(rs.getLong("delivery_status_id"))
                    .invoiceId(rs.getLong("invoice_id"))
                    
                    // Enum 변환
                    .status(DeliveryStatusType.from(rs.getString("status")))
                    .deliveryChannel(DeliveryChannelType.from(rs.getString("delivery_channel")))
                    
                    .retryCount(rs.getInt("retry_count"))
                    
                    // 2. 유틸리티 메서드를 통한 시간 변환 (가독성 확보)
                    .lastAttemptAt(getLocalDateTime(rs, "last_attempt_at"))
                    .createdAt(getLocalDateTime(rs, "created_at"))
                    .build();
        }

        /**
         * Timestamp를 LocalDateTime으로 안전하게 변환하는 유틸리티 메서드
         */
        private LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
            Timestamp ts = rs.getTimestamp(columnName);
            return (ts != null) ? ts.toLocalDateTime() : null;
        }
    }
}
