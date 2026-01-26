package com.mycom.myapp.sendapp.delivery.repository;

import com.mycom.myapp.sendapp.delivery.entity.DeliveryUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class DeliveryUserRepository {

    private final JdbcTemplate jdbcTemplate;
    private static final DeliveryUserRowMapper ROW_MAPPER = new DeliveryUserRowMapper();

    public List<DeliveryUser> findAllUsersByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return Collections.emptyList();

        String inSql = String.join(",", Collections.nCopies(userIds.size(), "?"));

        String sql = String.format("""
            SELECT 
                u.users_id, u.name, u.email, u.phone, u.is_withdrawn,
                s.preferred_hour,
                s.preferred_day  
            FROM users u
            LEFT JOIN users_delivery_settings s ON u.users_id = s.users_id 
            WHERE u.users_id IN (%s)
        """, inSql);

        return jdbcTemplate.query(sql, ROW_MAPPER, userIds.toArray());
    }

    private static final class DeliveryUserRowMapper implements RowMapper<DeliveryUser> {
        @Override
        public DeliveryUser mapRow(ResultSet rs, int rowNum) throws SQLException {
            DeliveryUser user = new DeliveryUser();
            user.setUsersId(rs.getLong("users_id"));
            user.setName(rs.getString("name"));
            user.setEmail(rs.getString("email"));
            user.setPhone(rs.getString("phone"));
            user.setIsWithdrawn(rs.getBoolean("is_withdrawn"));
            
            user.setPreferredHour(rs.getObject("preferred_hour", Integer.class));
            user.setPreferredDay(rs.getObject("preferred_day", Integer.class)); // 추가됨
            
            return user;
        }
    }
}