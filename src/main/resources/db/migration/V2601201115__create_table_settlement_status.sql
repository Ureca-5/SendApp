-- settlement_status 테이블 생성

CREATE TABLE `settlement_status` (
                                     `invoice_id` BIGINT NOT NULL COMMENT '월별 청구서 식별자 (PK/FK)',
                                     `status` VARCHAR(20) NOT NULL COMMENT 'NONE, READY, PROCESSING, COMPLETED, FAILED',
                                     `last_attempt_at` DATETIME(6) NOT NULL COMMENT '마지막 정산 시도 일시',
                                     `created_at` DATETIME(6) NOT NULL COMMENT '레코드 생성 일시',
                                     PRIMARY KEY (`invoice_id`),
                                     CONSTRAINT `FK_settlement_status_invoice`
                                         FOREIGN KEY (`invoice_id`)
                                             REFERENCES `monthly_invoice` (`invoice_id`)
                                             ON UPDATE RESTRICT
                                             ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;