package com.mycom.myapp.sendapp.admin.invoice.dao;

import com.mycom.myapp.sendapp.admin.invoice.dto.BillRowRawDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BillDao {

    private final JdbcTemplate jdbcTemplate;

    public BillDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count(Integer billingYyyymm, String keyword, Long invoiceId) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM monthly_invoice mi
            JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);

        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, keyword, invoiceId);

        Long result = jdbcTemplate.queryForObject(sql.toString(), args.toArray(), Long.class);
        return result == null ? 0 : result;
    }

    /**
     * ✅ Service에서 사용하는 표준 메서드명 (bills.html용)
     */
    public List<BillRowRawDTO> list(Integer billingYyyymm, String keyword, Long invoiceId, int size, int offset) {
        return find(billingYyyymm, keyword, invoiceId, size, offset);
    }

    /**
     * ✅ Service에서 사용하는 표준 메서드명 (bills.html용)
     */
    public BillRowRawDTO getBill(long invoiceId) {
        return findOne(invoiceId);
    }

    /**
     * (호환 유지) 기존 이름
     */
    public List<BillRowRawDTO> find(Integer billingYyyymm, String keyword, Long invoiceId, int size, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              mi.invoice_id,
              mi.users_id,
              mi.billing_yyyymm,
              mi.total_amount,
              mi.total_discount_amount,
              mi.due_date,
              mi.created_at,
              u.name AS user_name
            FROM monthly_invoice mi
            JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);

        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, keyword, invoiceId);

        sql.append(" ORDER BY mi.invoice_id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
            long invoiceIdVal = rs.getLong("invoice_id");
            long usersId = rs.getLong("users_id");
            int yyyymm = rs.getInt("billing_yyyymm");

            long totalAmount = rs.getLong("total_amount");
            long totalDiscountAmount = rs.getLong("total_discount_amount");

            Date dueDateSql = rs.getDate("due_date");
            LocalDate dueDate = dueDateSql == null ? null : dueDateSql.toLocalDate();

            Timestamp createdAtSql = rs.getTimestamp("created_at");
            LocalDateTime createdAt = createdAtSql == null ? null : createdAtSql.toLocalDateTime();

            String userName = rs.getString("user_name");

            return new BillRowRawDTO(
                    invoiceIdVal,
                    usersId,
                    yyyymm,
                    totalAmount,
                    totalDiscountAmount,
                    dueDate,
                    createdAt,
                    userName
            );
        });
    }

    /**
     * (호환 유지) 기존 이름
     */
    public BillRowRawDTO findOne(long invoiceId) {
        List<BillRowRawDTO> rows = find(null, null, invoiceId, 1, 0);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void applyWhere(StringBuilder sql, List<Object> args, Integer billingYyyymm, String keyword, Long invoiceId) {
        if (billingYyyymm != null) {
            sql.append(" AND mi.billing_yyyymm = ? ");
            args.add(billingYyyymm);
        }

        if (invoiceId != null) {
            sql.append(" AND mi.invoice_id = ? ");
            args.add(invoiceId);
        }

        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();

            // 숫자면 users_id로 검색, 아니면 name like 검색
            if (k.chars().allMatch(Character::isDigit)) {
                sql.append(" AND mi.users_id = ? ");
                args.add(Long.parseLong(k));
            } else {
                sql.append(" AND u.name LIKE ? ");
                args.add("%" + k + "%");
            }
        }
    }
}
