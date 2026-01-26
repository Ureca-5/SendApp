package com.mycom.myapp.sendapp.batch.repository.invoice;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.RowMapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@RequiredArgsConstructor
@Repository
public class MonthlyInvoiceRepositoryImpl implements MonthlyInvoiceRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MonthlyInvoiceRowDto> ID_ROW_MAPPER =
            new RowMapper<>() {
                @Override
                public MonthlyInvoiceRowDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Long usersId = rs.getLong("users_id");
                    if (rs.wasNull()) usersId = null;

                    Long invoiceId = rs.getLong("invoice_id");
                    if (rs.wasNull()) invoiceId = null;

                    return MonthlyInvoiceRowDto.builder()
                            .usersId(usersId)
                            .invoiceId(invoiceId)
                            .build();
                }
            };

    private static final RowMapper<MonthlyInvoiceRowDto> HEADER_ROW_MAPPER =
            new RowMapper<>() {
                @Override
                public MonthlyInvoiceRowDto mapRow(ResultSet rs, int rowNum) throws SQLException {
                    Long invoiceId = rs.getLong("invoice_id");
                    if (rs.wasNull()) invoiceId = null;
                    Long usersId = rs.getLong("users_id");
                    if (rs.wasNull()) usersId = null;

                    return MonthlyInvoiceRowDto.builder()
                            .invoiceId(invoiceId)
                            .usersId(usersId)
                            .billingYyyymm(rs.getInt("billing_yyyymm"))
                            .totalPlanAmount(rs.getLong("total_plan_amount"))
                            .totalAddonAmount(rs.getLong("total_addon_amount"))
                            .totalEtcAmount(rs.getLong("total_etc_amount"))
                            .totalDiscountAmount(rs.getLong("total_discount_amount"))
                            .totalAmount(rs.getLong("total_amount"))
                            .dueDate(rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate())
                            .createdAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime())
                            .expiredAt(rs.getDate("expired_at") == null ? null : rs.getDate("expired_at").toLocalDate())
                            .build();
                }
            };

    @Override
    public int[] batchInsert(List<MonthlyInvoiceRowDto> headers) {
        if (headers == null || headers.isEmpty()) {
            return new int[0];
        }

        // 컬럼명은 실제 스키마에 맞게 조정하세요.
        // (예: PK 컬럼이 invoice_id, totals 컬럼명이 다르면 수정)
        String sql = """
            INSERT INTO monthly_invoice (
                  users_id,
                  billing_yyyymm,
                  total_plan_amount,
                  total_addon_amount,
                  total_etc_amount,
                  total_discount_amount,
                  total_amount,
                  due_date,
                  created_at,
                  expired_at
            ) VALUES (
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?
            )
            """;

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MonthlyInvoiceRowDto h = headers.get(i);

                // users_id, billing_yyyymm는 필수
                ps.setLong(1, h.getUsersId());
                ps.setInt(2, h.getBillingYyyymm());

                // totals는 처음엔 0 또는 null 정책 중 택1
                // 여기서는 null 방지 위해 0L 기본값 권장
                ps.setLong(3, defaultLong(h.getTotalPlanAmount()));
                ps.setLong(4, defaultLong(h.getTotalAddonAmount()));
                ps.setLong(5, defaultLong(h.getTotalEtcAmount()));
                ps.setLong(6, defaultLong(h.getTotalDiscountAmount()));
                ps.setLong(7, defaultLong(h.getTotalAmount()));

                // due_date: LocalDate
                if (h.getDueDate() != null) {
                    ps.setObject(8, h.getDueDate());
                } else {
                    ps.setObject(8, null);
                }

                // created_at: LocalDateTime (processor에서 초기화)
                LocalDateTime createdAt = h.getCreatedAt();
                ps.setObject(9, createdAt);

                // expired_at: LocalDate (processor에서 초기화)
                if (h.getExpiredAt() != null) {
                    ps.setObject(10, h.getExpiredAt());
                } else {
                    ps.setObject(10, null);
                }

            }

            @Override
            public int getBatchSize() {
                return headers.size();
            }
        });
    }


    @Override
    public List<MonthlyInvoiceRowDto> findIdsByUsersIdsAndYyyymm(List<Long> usersIds, Integer billingYyyymm) {
        if (billingYyyymm == null) {
            throw new IllegalArgumentException("billingYyyymm is required.");
        }
        if (usersIds == null || usersIds.isEmpty()) {
            return Collections.emptyList();
        }

        String inClause = String.join(",", usersIds.stream().map(u -> "?").toArray(String[]::new));

        String sql = String.format("""
            SELECT
                  users_id,
                  invoice_id
            FROM monthly_invoice
            WHERE users_id IN (%s)
              AND billing_yyyymm = ?
            """, inClause);

        Object[] args = new Object[usersIds.size() + 1];
        for (int i = 0; i < usersIds.size(); i++) {
            args[i] = usersIds.get(i);
        }
        args[usersIds.size()] = billingYyyymm;

        return jdbcTemplate.query(sql, ID_ROW_MAPPER, args);
    }

    @Override
    public int[] batchUpdateTotals(List<MonthlyInvoiceRowDto> headers) {
        if (headers == null || headers.isEmpty()) {
            return new int[0];
        }

        // invoice_id(PK) 기준으로 totals 갱신
        String sql = """
            UPDATE monthly_invoice
            SET
                  total_plan_amount = ?,
                  total_addon_amount = ?,
                  total_etc_amount = ?,
                  total_discount_amount = ?,
                  total_amount = ?
            WHERE invoice_id = ?
            """;

        return jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MonthlyInvoiceRowDto h = headers.get(i);

                if (h.getInvoiceId() == null) {
                    throw new IllegalArgumentException("invoiceId is required for batchUpdateTotals.");
                }

                ps.setLong(1, defaultLong(h.getTotalPlanAmount()));
                ps.setLong(2, defaultLong(h.getTotalAddonAmount()));
                ps.setLong(3, defaultLong(h.getTotalEtcAmount()));
                ps.setLong(4, defaultLong(h.getTotalDiscountAmount()));
                ps.setLong(5, defaultLong(h.getTotalAmount()));

                ps.setLong(6, h.getInvoiceId());
            }

            @Override
            public int getBatchSize() {
                return headers.size();
            }
        });
    }

    @Override
    public List<MonthlyInvoiceRowDto> findByInvoiceIds(List<Long> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return Collections.emptyList();
        }

        String inClause = String.join(",", invoiceIds.stream().map(u -> "?").toArray(String[]::new));

        String sql = String.format("""
            SELECT
                  invoice_id,
                  users_id,
                  billing_yyyymm,
                  total_plan_amount,
                  total_addon_amount,
                  total_etc_amount,
                  total_discount_amount,
                  total_amount,
                  due_date,
                  created_at,
                  expired_at
            FROM monthly_invoice
            WHERE invoice_id IN (%s)
            """, inClause);

        Object[] args = invoiceIds.toArray();

        return jdbcTemplate.query(sql, HEADER_ROW_MAPPER, args);
    }

    private long defaultLong(Long v) {
        return v != null ? v : 0L;
    }
}
