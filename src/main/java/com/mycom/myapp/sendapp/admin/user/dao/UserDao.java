package com.mycom.myapp.sendapp.admin.user.dao;

import com.mycom.myapp.sendapp.admin.user.dto.UserRowRawDTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class UserDao {

    private final JdbcTemplate jdbcTemplate;

    public UserDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countUsers(String keyword, Boolean withdrawn) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM users WHERE 1=1 ");
        List<Object> args = new ArrayList<>();

        applyWhere(sql, args, keyword, withdrawn);

        Integer cnt = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return cnt == null ? 0 : cnt;
    }

    public List<UserRowRawDTO> findUsers(String keyword, Boolean withdrawn, int size, int offset) {
        StringBuilder sql = new StringBuilder("""
            SELECT users_id, name, email, phone, joined_at, is_withdrawn
            FROM users
            WHERE 1=1
        """);
        List<Object> args = new ArrayList<>();

        applyWhere(sql, args, keyword, withdrawn);

        sql.append(" ORDER BY users_id DESC LIMIT ? OFFSET ? ");
        args.add(size);
        args.add(offset);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("joined_at");
            LocalDateTime joinedAt = (ts == null) ? null : ts.toLocalDateTime();
            return new UserRowRawDTO(
                    rs.getLong("users_id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    joinedAt,
                    rs.getBoolean("is_withdrawn")
            );
        }, args.toArray());
    }

    private static void applyWhere(StringBuilder sql, List<Object> args, String keyword, Boolean withdrawn) {
        if (keyword != null && !keyword.isBlank()) {
            // keyword가 숫자면 users_id exact 우선, 아니면 name like
            String k = keyword.trim();
            if (k.matches("\\d+")) {
                sql.append(" AND users_id = ? ");
                args.add(Long.parseLong(k));
            } else {
                sql.append(" AND name LIKE ? ");
                args.add("%" + k + "%");
            }
        }
        if (withdrawn != null) {
            sql.append(" AND is_withdrawn = ? ");
            args.add(withdrawn);
        }
    }
}
