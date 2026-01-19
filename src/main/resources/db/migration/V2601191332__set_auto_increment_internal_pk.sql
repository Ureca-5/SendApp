-- V2601191332__set_auto_increment_internal_pk.sql
-- 목적: 내부 생성 PK(7개 테이블)만 AUTO_INCREMENT로 전환
-- 주의: monthly_invoice.invoice_id는 여러 테이블에서 FK로 참조되므로 FK drop -> modify -> FK re-add 순서 필요

-- =========================
-- 1) FK DROP (invoice_id 참조 관계)
-- =========================

-- monthly_invoice_detail.invoice_id -> monthly_invoice.invoice_id
ALTER TABLE `monthly_invoice_detail`
  DROP FOREIGN KEY `FK_monthly_invoice_detail_invoice`;

-- delivery_status.invoice_id -> monthly_invoice.invoice_id
ALTER TABLE `delivery_status`
  DROP FOREIGN KEY `FK_delivery_status_invoice`;

-- delivery_history.invoice_id -> monthly_invoice.invoice_id
ALTER TABLE `delivery_history`
  DROP FOREIGN KEY `FK_delivery_history_invoice`;


-- =========================
-- 2) FK DROP (batch_attempt 참조 관계)
-- =========================

-- monthly_invoice_batch_fail.attempt_id -> monthly_invoice_batch_attempt.attempt_id
ALTER TABLE `monthly_invoice_batch_fail`
  DROP FOREIGN KEY `FK_monthly_invoice_batch_fail_attempt`;


-- =========================
-- 3) PK AUTO_INCREMENT 적용
-- =========================

-- Core
ALTER TABLE `monthly_invoice`
  MODIFY `invoice_id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `monthly_invoice_detail`
  MODIFY `detail_id` BIGINT NOT NULL AUTO_INCREMENT;

-- Delivery
ALTER TABLE `delivery_status`
  MODIFY `delivery_status_id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `delivery_history`
  MODIFY `delivery_history_id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `delivery_summary`
  MODIFY `delivery_summary_id` BIGINT NOT NULL AUTO_INCREMENT;

-- Batch
ALTER TABLE `monthly_invoice_batch_attempt`
  MODIFY `attempt_id` BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE `monthly_invoice_batch_fail`
  MODIFY `fail_id` BIGINT NOT NULL AUTO_INCREMENT;


-- =========================
-- 4) FK RE-ADD (원래 정의대로 복구)
-- =========================

ALTER TABLE `monthly_invoice_detail`
  ADD CONSTRAINT `FK_monthly_invoice_detail_invoice`
  FOREIGN KEY (`invoice_id`)
  REFERENCES `monthly_invoice` (`invoice_id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `delivery_status`
  ADD CONSTRAINT `FK_delivery_status_invoice`
  FOREIGN KEY (`invoice_id`)
  REFERENCES `monthly_invoice` (`invoice_id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `delivery_history`
  ADD CONSTRAINT `FK_delivery_history_invoice`
  FOREIGN KEY (`invoice_id`)
  REFERENCES `monthly_invoice` (`invoice_id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;

ALTER TABLE `monthly_invoice_batch_fail`
  ADD CONSTRAINT `FK_monthly_invoice_batch_fail_attempt`
  FOREIGN KEY (`attempt_id`)
  REFERENCES `monthly_invoice_batch_attempt` (`attempt_id`)
  ON UPDATE RESTRICT
  ON DELETE RESTRICT;
