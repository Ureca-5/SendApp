-- subscribe_billing_history의 복합 인덱스 구성 요소 추가
-- 추가 컬럼: 기기 식별자
-- 추가 이유: 구독 서비스 원천 데이터 조회 시 '사용 기간' 세그먼트 확정을 빠르게 하기 위함
-- 새로운 복합 인덱스 추가 후 기존 복합 인덱스 제거

alter table subscribe_billing_history add index INDX_subscribe_billing_history_users_yyyymm_device (users_id, billing_yyyymm, device_id);
drop index INDX_subscribe_billing_history_users_yyyymm on subscribe_billing_history;