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
    
    // RowMapper를 static 상수로 만들어 재사용 (GC 효율)
    private static final DeliveryUserRowMapper ROW_MAPPER = new DeliveryUserRowMapper();

    /**
     * ID 리스트를 받아 users 테이블에서 배송에 필요한 정보만 조회
     * SELECT ... WHERE users_id IN (?, ?, ...)
     */
    public List<DeliveryUser> findAllUsersByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. IN 절 파라미터 개수만큼 '?' 생성 (?, ?, ?)
        String inSql = String.join(",", Collections.nCopies(userIds.size(), "?"));

        // 2. SQL 작성
        String sql = String.format("""
            SELECT users_id, name, email, phone, is_withdrawn
            FROM users
            WHERE users_id IN (%s)
        """, inSql);

        // 3. 실행 (Set -> Array 변환하여 전달)
        return jdbcTemplate.query(sql, ROW_MAPPER, userIds.toArray());
    }

    /**
     * 내부 정적 클래스로 RowMapper 구현 (DeliveryHistory 스타일)
     */
    private static final class DeliveryUserRowMapper implements RowMapper<DeliveryUser> {
        @Override
        public DeliveryUser mapRow(ResultSet rs, int rowNum) throws SQLException {
            return DeliveryUser.builder()
                    // DB 컬럼명(users_id) -> 자바 필드명(userId) 매핑
                    .userId(rs.getLong("users_id"))
                    .name(rs.getString("name"))
                    .email(rs.getString("email"))
                    .phone(rs.getString("phone"))
                    .isWithdrawn(rs.getBoolean("is_withdrawn"))
                    .build();
        }
    }
}