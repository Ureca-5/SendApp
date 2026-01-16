package com.mycom.myapp.sendapp.delivery.repository;

import com.mycom.myapp.sendapp.delivery.entity.DeliverySummary;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
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
public class DeliverySummaryRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final DeliverySummaryRowMapper ROW_MAPPER = new DeliverySummaryRowMapper();

    /**
     * 내부 정적 RowMapper 클래스
     */
    private static final class DeliverySummaryRowMapper implements RowMapper<DeliverySummary> {
        @Override
        public DeliverySummary mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DeliverySummary.builder()
                    .deliverySummaryId(rs.getLong("delivery_summary_id"))
                    .billingYyyymm(rs.getInt("billing_yyyymm"))
                    
                    // Enum 변환 (안전한 정적 팩토리 메서드 활용)
                    .deliveryChannel(DeliveryChannelType.from(rs.getString("delivery_channel")))
                    
                    .totalAttemptCount(rs.getInt("total_attempt_count"))
                    .successCount(rs.getInt("success_count"))
                    .failCount(rs.getInt("fail_count"))
                    
                    // 정밀도가 중요한 통계용 DECIMAL 매핑
                    .successRate(rs.getBigDecimal("success_rate"))
                    
                    // 2. 유틸리티 메서드를 통한 시간 변환 (스타일 통일)
                    .createdAt(getLocalDateTime(rs, "created_at"))
                    .updatedAt(getLocalDateTime(rs, "updated_at"))
                    .build();
        }

        /**
         * Timestamp를 LocalDateTime으로 안전하게 변환하는 공통 유틸리티
         */
        private LocalDateTime getLocalDateTime(ResultSet rs, String columnName) throws SQLException {
            Timestamp ts = rs.getTimestamp(columnName);
            return (ts != null) ? ts.toLocalDateTime() : null;
        }
    }
}