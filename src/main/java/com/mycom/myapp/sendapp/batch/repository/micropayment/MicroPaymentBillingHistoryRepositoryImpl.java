package com.mycom.myapp.sendapp.batch.repository.micropayment;

import com.mycom.myapp.sendapp.batch.dto.MicroPaymentBillingHistoryRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MicroPaymentBillingHistoryRepositoryImpl implements MicroPaymentBillingHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<MicroPaymentBillingHistoryRowDto> findPageByUsersIdsAndYyyymmKeyset(
            Integer targetYyyymm,
            List<Long> usersIds,
            Long lastId,
            Integer limit
    ) {
        if (targetYyyymm == null) {
            throw new IllegalArgumentException("targetYyyymm is required.");
        }
        if (usersIds == null || usersIds.isEmpty()) {
            return Collections.emptyList();
        }

        long safeLastId = (lastId == null) ? 0L : lastId;
        int safeLimit = (limit == null || limit <= 0) ? 5000 : limit;

        // IN 절 placeholder
        String inClause = String.join(",", usersIds.stream().map(u -> "?").toArray(String[]::new));

        // 인덱스 (users_id, billing_yyyymm, micro_payment_billing_history_id) 활용 목표:
        // WHERE users_id IN (...) AND billing_yyyymm = ? AND micro_payment_billing_history_id > ?
        // ORDER BY users_id, billing_yyyymm, micro_payment_billing_history_id
        String sql = String.format("""
            SELECT
                  micro_payment_billing_history_id,
                  users_id,
                  billing_yyyymm,
                  micro_payment_service_id,
                  service_name,
                  origin_amount,
                  discount_amount,
                  total_amount,
                  created_at
            FROM micro_payment_billing_history
            WHERE users_id IN (%s)
              AND billing_yyyymm = ?
              AND micro_payment_billing_history_id > ?
            ORDER BY users_id ASC, billing_yyyymm ASC, micro_payment_billing_history_id ASC
            LIMIT ?
            """, inClause);

        // args: [usersIds..., targetYyyymm, lastId, limit]
        Object[] args = new Object[usersIds.size() + 3];
        int idx = 0;
        for (Long u : usersIds) {
            args[idx++] = u;
        }
        args[idx++] = targetYyyymm;
        args[idx++] = safeLastId;
        args[idx] = safeLimit;

        return jdbcTemplate.query(sql, (rs, rowNum) -> MicroPaymentBillingHistoryRowDto.builder()
                .microPaymentBillingHistoryId(rs.getLong("micro_payment_billing_history_id"))
                .usersId(rs.getLong("users_id"))
                .billingYyyymm(rs.getInt("billing_yyyymm"))
                .microPaymentServiceId(rs.getLong("micro_payment_service_id"))
                .serviceName(rs.getString("service_name"))
                .originAmount(rs.getLong("origin_amount"))
                .discountAmount(rs.getLong("discount_amount"))
                .totalAmount(rs.getLong("total_amount"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .build(), args);

    }
}
