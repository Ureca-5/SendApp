package com.mycom.myapp.sendapp.batch.repository.category;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ServiceCategoryRepositoryImpl implements ServiceCategoryRepository {
    private final JdbcTemplate jdbcTemplate;
    @Override
    public Map<String, Integer> findAllServiceCategory() {
        String sql = """
        SELECT invoice_category_id, name FROM invoice_category;
        """;

        // 1. 모든 '청구서 카테고리' 정보 조회
        // 2. 카테고리명, 식별자를 Map으로 매핑하여 return
        return jdbcTemplate.query(sql, rs -> {
            Map<String, Integer> map = new HashMap<>();
            while(rs.next()) {
                map.put(rs.getString("name"),
                        rs.getInt("invoice_category_id")
                );
            }
            return map;
        });
    }
}
