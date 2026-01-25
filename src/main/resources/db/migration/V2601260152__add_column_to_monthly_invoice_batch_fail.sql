-- monthly_invoice_batch_fail 테이블에 'invoice_id' 외래키 추가

truncate monthly_invoice_batch_fail;

ALTER TABLE monthly_invoice_batch_fail
    ADD COLUMN invoice_id BIGINT not null,
ADD CONSTRAINT fk_batch_fail_invoice
FOREIGN KEY (invoice_id) REFERENCES monthly_invoice(invoice_id);