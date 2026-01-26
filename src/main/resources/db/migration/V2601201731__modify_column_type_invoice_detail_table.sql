-- '청구서 상세' 테이블의 '서비스 이용 시작 일자' 및 '서비스 이용 종료 일자'의 타입을 기존 DATETIME(6)에서 DATE로 변경
-- 변경 사유: 설계 실수(구독 원천 데이터 테이블의 '구독 시작 일자'와 타입 불일치)

alter table monthly_invoice_detail modify usage_start_date date not null;
alter table monthly_invoice_detail modify usage_end_date date not null;