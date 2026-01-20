package com.mycom.myapp.sendapp.batch.repository.subscribe;

import com.mycom.myapp.sendapp.batch.dto.SubscribeBillingHistoryRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@RequiredArgsConstructor
@Repository
public class SubscribeBillingHistoryRepositoryImpl implements SubscribeBillingHistoryRepository {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<SubscribeBillingHistoryRowDto> findByUsersIdsAndYyyymm(
            Integer targetYyyymm,
            List<Long> usersIds
    ) {
        if (usersIds == null || usersIds.isEmpty()) {
            return List.of();
        }

        String inClause = String.join(",", usersIds.stream().map(u -> "?").toArray(String[]::new));

        String sql = String.format("""
            SELECT
                  subscribe_billing_history_id,
                  users_id,
                  device_id,
                  subscribe_service_id,
                  service_name,
                  subscription_start_date,
                  origin_amount,
                  discount_amount,
                  total_amount,
                  billing_yyyymm
            FROM subscribe_billing_history
            WHERE users_id IN (%s)
              AND billing_yyyymm = ?
            ORDER BY users_id ASC, billing_yyyymm ASC, device_id ASC
            """, inClause);

        Object[] args = new Object[1 + usersIds.size()];
        for (int i = 0; i < usersIds.size(); i++) {
            args[i] = usersIds.get(i);
        }
        args[usersIds.size()] = targetYyyymm;

        return jdbcTemplate.query(sql, (rs, rowNum) -> SubscribeBillingHistoryRowDto.builder()
                .subscribeBillingHistoryId(rs.getLong("subscribe_billing_history_id"))
                .usersId(rs.getLong("users_id"))
                .deviceId(rs.getLong("device_id"))
                .subscribeServiceId(rs.getInt("subscribe_service_id"))
                .serviceName(rs.getString("service_name"))
                .subscriptionStartDate(rs.getDate("subscription_start_date").toLocalDate())
                .originAmount(rs.getLong("origin_amount"))
                .discountAmount(rs.getLong("discount_amount"))
                .totalAmount(rs.getLong("total_amount"))
                .billingYyyymm(rs.getInt("billing_yyyymm"))
                .build(), args);
    }
}
