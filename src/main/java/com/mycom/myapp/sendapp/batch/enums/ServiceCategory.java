package com.mycom.myapp.sendapp.batch.enums;

import lombok.Getter;

@Getter
public enum ServiceCategory {
    PLAN("요금제"),
    ADDON("부가서비스"),
    ETC_PLAN("기타 요금제"),
    MICRO("단건 결제");

    private final String dbCategoryName;
    ServiceCategory(String dbCategoryName) {
        this.dbCategoryName = dbCategoryName;
    }
}
