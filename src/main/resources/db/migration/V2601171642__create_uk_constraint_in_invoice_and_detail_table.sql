-- 청구서 헤더, 청구서 상세 테이블에 UK 제약조건 생성

-- 청구서 헤더 UK: (users_id, billing_yyyymm)
alter table monthly_invoice add constraint UK_monthly_invoice_users_yyyymm unique (users_id, billing_yyyymm);
-- 청구서 상세 UK: (invoice_id, category_id, billing_history_id)
alter table monthly_invoice_detail add constraint UK_monthly_invoice_category_billing unique (invoice_id, invoice_category_id, billing_history_id);