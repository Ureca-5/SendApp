package com.mycom.myapp.sendapp.batch.repository.category;

import java.util.Map;

public interface ServiceCategoryRepository {
    /**
     * 모든 '청구 카테고리' 항목 조회 후 Map에 카테고리명과 식별자를 매핑
     * @return 매핑된 Map
     */
    Map<String, Integer> findAllServiceCategory();
}
