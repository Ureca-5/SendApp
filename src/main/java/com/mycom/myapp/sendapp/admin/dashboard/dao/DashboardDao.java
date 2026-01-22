// src/main/java/com/mycom/myapp/sendapp/admin/dashboard/dao/DashboardDao.java
package com.mycom.myapp.sendapp.admin.dashboard.dao;

import com.mycom.myapp.sendapp.admin.dashboard.dto.RecentSendingHistoryVM;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class DashboardDao {

    private final JdbcTemplate jdbcTemplate;

    public DashboardDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 최근 발송 이력 N건
     * - billingYyyymm 기준으로 monthly_invoice와 조인해서 필터링
     *
     * NOTE:
     * - delivery_history / delivery_status 테이블에는 billing_yyyymm 컬럼이 없으므로,
     *   monthly_invoice.billing_yyyymm을 기준으로 필터링해야 함.
     * - delivery_history에는 channel 컬럼이 없고 delivery_channel 컬럼이 존재함.
     */
    public List<RecentSendingHistoryVM> recentSendingHistory(int billingYyyymm, int limit) {
        int safeLimit = limit;
        if (safeLimit < 1) safeLimit = 1;
        if (safeLimit > 50) safeLimit = 50;

        String sql = """
            SELECT
                h.invoice_id,
                h.delivery_channel AS channel,
                h.status,
                h.requested_at,
                h.error_message
            FROM delivery_history h
            JOIN monthly_invoice mi ON mi.invoice_id = h.invoice_id
            WHERE mi.billing_yyyymm = ?
            ORDER BY h.requested_at DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, recentSendingHistoryRowMapper(), billingYyyymm, safeLimit);
    }

    private RowMapper<RecentSendingHistoryVM> recentSendingHistoryRowMapper() {
        return new RowMapper<>() {
            @Override
            public RecentSendingHistoryVM mapRow(ResultSet rs, int rowNum) throws SQLException {
                long invoiceId = rs.getLong("invoice_id");
                String channel = rs.getString("channel");
                String status = rs.getString("status");

                Timestamp requestedAtTs = rs.getTimestamp("requested_at");
                String requestedAtText = "-";
                if (requestedAtTs != null) {
                    LocalDateTime dt = requestedAtTs.toLocalDateTime();
                    // 템플릿에서 그대로 표시하는 문자열 계약
                    requestedAtText = dt.toString();
                }

                String errorMessage = rs.getString("error_message");

                return new RecentSendingHistoryVM(
                        invoiceId,
                        channel,
                        status,
                        requestedAtText,
                        errorMessage
                );
            }
        };
    }
}
