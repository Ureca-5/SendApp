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

    public long count(Integer billingYyyymm, String keyword) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM monthly_invoice mi
            JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);

        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, keyword);

        Long result = jdbcTemplate.queryForObject(sql.toString(), args.toArray(), Long.class);
        return result == null ? 0 : result;
    }

    public List<BillRowRawDTO> find(Integer billingYyyymm, String keyword, int size, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              mi.invoice_id,
              mi.users_id,
              mi.billing_yyyymm,

              -- ✅ 최종 DB 컬럼명(total_*)을 화면 DTO의 의미에 맞게 alias
              mi.total_plan_amount    AS plan_amount,
              mi.total_addon_amount   AS addon_amount,
              mi.total_etc_amount     AS etc_amount,
              mi.total_discount_amount AS discount_amount,
              mi.total_amount,

              mi.due_date,
              mi.created_at,

              u.name  AS user_name,
              u.phone AS phone_enc
            FROM monthly_invoice mi
            JOIN users u ON mi.users_id = u.users_id
            WHERE 1=1
        """);

        List<Object> args = new ArrayList<>();
        applyWhere(sql, args, billingYyyymm, keyword);

        sql.append(" ORDER BY mi.invoice_id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
            long invoiceId = rs.getLong("invoice_id");
            long usersId = rs.getLong("users_id");
            int yyyymm = rs.getInt("billing_yyyymm");

            long planAmount = rs.getLong("plan_amount");
            long addonAmount = rs.getLong("addon_amount");
            long etcAmount = rs.getLong("etc_amount");
            long discountAmount = rs.getLong("discount_amount");
            long totalAmount = rs.getLong("total_amount");

            Date dueDateSql = rs.getDate("due_date");
            LocalDate dueDate = dueDateSql == null ? null : dueDateSql.toLocalDate();

            Timestamp createdAtSql = rs.getTimestamp("created_at");
            LocalDateTime createdAt = createdAtSql == null ? null : createdAtSql.toLocalDateTime();

            String userName = rs.getString("user_name");
            String phoneEnc = rs.getString("phone_enc");

            return new BillRowRawDTO(
                invoiceId,
                usersId,
                yyyymm,
                planAmount,
                addonAmount,
                etcAmount,
                discountAmount,
                totalAmount,
                dueDate,
                createdAt,
                userName,
                phoneEnc
            );
        });
    }

    private void applyWhere(StringBuilder sql, List<Object> args, Integer billingYyyymm, String keyword) {
        if (billingYyyymm != null) {
            sql.append(" AND mi.billing_yyyymm = ? ");
            args.add(billingYyyymm);
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
