-- 기존 테이블에 복합 유니크 키 추가

ALTER TABLE `delivery_history` 
ADD UNIQUE KEY `UK_invoice_channel_attempt` (`invoice_id`, `delivery_channel`, `attempt_no`);