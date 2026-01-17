-- 두 종류의 원천 데이터 테이블에 (billing_yyyymm, users_id) 조합의 복합 인덱스 생성
-- 기존에 존재하던 users_id FK 제약조건을 위한 인덱스가 제거되고, 이 복합 인덱스로 그 역할을 대신 수행

alter table subscribe_billing_history add index INDX_subscribe_billing_history_users_yyyymm (users_id, billing_yyyymm);

alter table micro_payment_billing_history add index INDX_micro_payment_billing_history_users_yyyymm (users_id, billing_yyyymm);