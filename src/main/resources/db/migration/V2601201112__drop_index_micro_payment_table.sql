-- 단건 결제 테이블에서 기존에 존재하던 복합 인덱스 제거
-- 이유: 새로 추가된 복합 인덱스만 사용하기 위함

drop index INDX_micro_payment_billing_history_users_yyyymm on micro_payment_billing_history;