-- 단건 결제 원천 데이터 테이블의 복합 인덱스 구성 요소 수정
-- (users_id, billing_yyyymm) -> (users_id, billing_yyyymm, micro_payment_billing_history_id)
-- 수정 이유: 정산을 위해 keyset 페이징 수행 시 조건문에 유일성을 만족하는 값을 포함하기 위함

alter table micro_payment_billing_history add index INDX_micro_payment_billing_history_users_yyyymm_pk (users_id, billing_yyyymm, micro_payment_billing_history_id);