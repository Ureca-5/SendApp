-- 기존 테이블에 유니크 키 추가

ALTER TABLE delivery_status
ADD CONSTRAINT UK_delivery_status_invoice UNIQUE (invoice_id);