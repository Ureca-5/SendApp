-- settlement_status_history 테이블 생성

CREATE TABLE `settlement_status_history` (
                                             `settlement_status_history_id` BIGINT NOT NULL,
                                             `invoice_id` BIGINT NOT NULL,
                                             `attempt_id` BIGINT NOT NULL,
                                             `from_status` VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '변경 전 상태',
                                             `to_status` VARCHAR(20) NOT NULL COMMENT '변경 후 상태',
                                             `changed_at` DATETIME(6) NOT NULL COMMENT '변경 일시',
                                             `reason_code` VARCHAR(50) NULL COMMENT '변경사유',
                                             PRIMARY KEY (`settlement_status_history_id`),
                                             CONSTRAINT `FK_settlement_status_history_invoice`
                                                 FOREIGN KEY (`invoice_id`)
                                                     REFERENCES `monthly_invoice` (`invoice_id`)
                                                     ON UPDATE RESTRICT
                                                     ON DELETE RESTRICT,
                                             CONSTRAINT `FK_settlement_status_history_attempt`
                                                 FOREIGN KEY (`attempt_id`)
                                                     REFERENCES `monthly_invoice_batch_attempt` (`attempt_id`)
                                                     ON UPDATE RESTRICT
                                                     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;