package com.mycom.myapp.sendapp.batch.support;

import com.mycom.myapp.sendapp.batch.enums.ServiceCategory;
import com.mycom.myapp.sendapp.batch.repository.category.ServiceCategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CategoryIdRegistry {
    private final ServiceCategoryRepository svcCategoryRepository;
    private final Map<ServiceCategory, Integer> registry = new EnumMap<>(ServiceCategory.class);

    @Autowired
    public CategoryIdRegistry(ServiceCategoryRepository svcCategoryRepository) {
        this.svcCategoryRepository = svcCategoryRepository;
    }

    @PostConstruct
    public void init() {
        Map<String, Integer> categorys = svcCategoryRepository.findAllServiceCategory();
        // 카테고리 조회 결과를 EnumMap으로 조립
        for(String categoryName : categorys.keySet()) {
            for(ServiceCategory serviceCategory : ServiceCategory.values()) {
                if(categoryName.equals(serviceCategory.getDbCategoryName())) {
                    registry.put(serviceCategory, categorys.get(categoryName));
                    break;
                }
            }
        }
        validateRequiredCategoryIds();
    }

    /**
     * 카테고리명, 식별자 매핑된 registry에서 특정 값만을 조회
     * @param serviceCategory enum
     * @return enum에 매핑되는 식별자 Integer 값
     */
    public Integer getCategoryId(@NonNull ServiceCategory serviceCategory) {
        return registry.get(serviceCategory);
    }

    private void validateRequiredCategoryIds() {
        List<String> missingCategories = new ArrayList<>();
        for(ServiceCategory serviceCategory : ServiceCategory.values()) {
            if(!registry.containsKey(serviceCategory)) {
                missingCategories.add(serviceCategory.toString());
            }
        }
        if(!missingCategories.isEmpty()) {
            throw new IllegalStateException("DB에 다음의 필수 카테고리에 대한 정보가 없습니다. 부재 카테고리 목록: "+missingCategories);
        }
    }
}
