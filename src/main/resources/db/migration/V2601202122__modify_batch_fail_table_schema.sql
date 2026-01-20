-- '정산 실패 이력' 테이블 컬럼 변경
-- 기존: 정산 실패 유저 정보 보관
-- 변경: 정산 실패 '원천 데이터' 보관

alter table monthly_invoice_batch_fail drop constraint FK_monthly_invoice_batch_fail_users;
alter table monthly_invoice_batch_fail drop column users_id;
alter table monthly_invoice_batch_fail drop column target_yyyymm;

alter table monthly_invoice_batch_fail add column invoice_category_id int not null,
    add constraint FK_monthly_invoice_batch_fail_category foreign key (invoice_category_id) references invoice_category(invoice_category_id);
alter table monthly_invoice_batch_fail add column billing_history_id bigint not null;