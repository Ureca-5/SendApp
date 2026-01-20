package com.mycom.myapp.sendapp.batch.repository.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Repository
public class MonthlyInvoiceRepositoryImpl implements MonthlyInvoiceRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void batchInsert(List<MonthlyInvoiceRowDto> headers) {

        String sql = """
            INSERT INTO monthly_invoice
                (users_id, billing_yyyymm, total_plan_amount, total_addon_amount,
                 total_etc_amount, total_discount_amount, total_amount,
                 due_date, created_at, expired_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {

            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MonthlyInvoiceRowDto dto = headers.get(i);
                ps.setLong(1, dto.getUsersId());
                ps.setInt(2, dto.getBillingYyyymm());
                ps.setLong(3, dto.getTotalPlanAmount());
                ps.setLong(4, dto.getTotalAddonAmount());
                ps.setLong(5, dto.getTotalEtcAmount());
                ps.setLong(6, dto.getTotalDiscountAmount());
                ps.setLong(7, dto.getTotalAmount());
                ps.setDate(8, java.sql.Date.valueOf(dto.getDueDate()));
                ps.setObject(9, dto.getCreatedAt());
                ps.setDate(10, java.sql.Date.valueOf(dto.getExpiredAt()));
            }

            @Override
            public int getBatchSize() {
                return headers.size();
            }
        });
    }

    @Override
    public Map<Long, Long> findInvoiceIdsByUsers(Integer targetYyyymm, List<Long> usersIds) {
        if (usersIds == null || usersIds.isEmpty()) {
            return Map.of();
        }

        String sql = """
            SELECT users_id, invoice_id
            FROM monthly_invoice
            WHERE billing_yyyymm = ?
              AND users_id IN (%s)
            """;

        // in절용 플레이스홀더 만들기
        String inClause = String.join(",", usersIds.stream().map(u -> "?").toArray(String[]::new));
        sql = String.format(sql, inClause);

        Object[] args = new Object[1 + usersIds.size()];
        args[0] = targetYyyymm;
        for (int i = 0; i < usersIds.size(); i++) {
            args[i + 1] = usersIds.get(i);
        }

        return jdbcTemplate.query(sql, rs -> {
            Map<Long, Long> map = new HashMap<>();
            while (rs.next()) {
                map.put(rs.getLong("users_id"), rs.getLong("invoice_id"));
            }
            return map;
        }, args);
    }
}
