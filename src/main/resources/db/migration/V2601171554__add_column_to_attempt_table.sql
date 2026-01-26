-- attempt 테이블에 '총 정산 대상 수' 컬럼 추가
-- 사유: '성공 비율' 계산을 위함

alter table monthly_invoice_batch_attempt add column target_count bigint not null default 0;