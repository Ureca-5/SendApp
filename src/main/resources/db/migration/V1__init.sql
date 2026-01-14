-- V1__init_schema.sql
-- MySQL 8.x / InnoDB / utf8mb4
-- Tables + PK/FK/NN/default only (indexes/UK are added later except those already defined)

-- NOTE:
-- 1) Flyway는 "DB 자체 생성(CREATE DATABASE)"을 기본으로 책임지기 어렵습니다.
--    이 스크립트는 "sendapp" DB에 연결된 상태에서 실행된다고 가정합니다.
-- 2) billing_history_id는 ERD 의도대로 "논리 FK(polymorphic)" 이므로 물리 FK를 걸지 않습니다.
-- 3) FK 삭제 연쇄를 피하기 위해 기본은 RESTRICT/NO ACTION을 사용합니다.

-- =========================
-- Master tables
-- =========================

CREATE TABLE `subscribe_category` (
                                      `subscribe_category_id` INT NOT NULL,
                                      `name` VARCHAR(255) NOT NULL COMMENT '요금제, 부가서비스 등',
                                      `created_at` DATETIME(6) NOT NULL,
                                      PRIMARY KEY (`subscribe_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `subscribe_service` (
                                     `subscribe_service_id` INT NOT NULL,
                                     `subscribe_service_category_id` INT NOT NULL,
                                     `name` VARCHAR(255) NOT NULL,
                                     `fee` BIGINT NOT NULL COMMENT '1 이상',
                                     `created_at` DATETIME(6) NOT NULL,
                                     `updated_at` DATETIME(6) NULL,
                                     PRIMARY KEY (`subscribe_service_id`),
                                     CONSTRAINT `FK_subscribe_service_category`
                                         FOREIGN KEY (`subscribe_service_category_id`)
                                             REFERENCES `subscribe_category` (`subscribe_category_id`)
                                             ON UPDATE RESTRICT
                                             ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `invoice_category` (
                                    `invoice_category_id` INT NOT NULL,
                                    `name` VARCHAR(255) NOT NULL COMMENT '요금제, 부가서비스, ETC',
                                    `created_at` DATETIME(6) NOT NULL,
                                    PRIMARY KEY (`invoice_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `micro_payment_service` (
                                         `micro_payment_service_id` INT NOT NULL,
                                         `name` VARCHAR(255) NOT NULL,
                                         `created_at` DATETIME(6) NOT NULL,
                                         PRIMARY KEY (`micro_payment_service_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================
-- Core tables
-- =========================

CREATE TABLE `users` (
                         `users_id` BIGINT NOT NULL,
                         `email` VARCHAR(255) NOT NULL,
                         `name` VARCHAR(255) NOT NULL,
                         `address` VARCHAR(255) NOT NULL,
                         `phone` VARCHAR(255) NOT NULL,
                         `joined_at` DATETIME(6) NOT NULL,
                         `is_withdrawn` TINYINT(1) NOT NULL,
                         `updated_at` DATETIME(6) NULL,
                         `payment_method` VARCHAR(20) NOT NULL,
                         `payment_info` VARCHAR(255) NOT NULL,
                         `billing_day` INT NOT NULL,
                         PRIMARY KEY (`users_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- users_device는 subscribe_billing_history가 참조하므로 users 다음에 생성
CREATE TABLE `users_device` (
                                `device_id` BIGINT NOT NULL,
                                `users_id` BIGINT NOT NULL,
                                `nickname` VARCHAR(255) NOT NULL,
                                `device_type` VARCHAR(20) NOT NULL,
                                `created_at` DATETIME(6) NOT NULL,
                                PRIMARY KEY (`device_id`),
                                CONSTRAINT `FK_users_device_users`
                                    FOREIGN KEY (`users_id`)
                                        REFERENCES `users` (`users_id`)
                                        ON UPDATE RESTRICT
                                        ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `monthly_invoice` (
                                   `invoice_id` BIGINT NOT NULL,
                                   `users_id` BIGINT NOT NULL,
                                   `billing_yyyymm` INT NOT NULL COMMENT '6자리 정수(YYYYMM)',
                                   `total_plan_amount` BIGINT NOT NULL DEFAULT 0,
                                   `total_addon_amount` BIGINT NOT NULL DEFAULT 0,
                                   `total_etc_amount` BIGINT NOT NULL DEFAULT 0,
                                   `total_discount_amount` BIGINT NOT NULL,
                                   `total_amount` BIGINT NOT NULL,
                                   `due_date` DATE NOT NULL,
                                   `created_at` DATETIME(6) NOT NULL,
                                   `expired_at` DATE NOT NULL COMMENT '생성일자 + 5년',
                                   PRIMARY KEY (`invoice_id`),
                                   CONSTRAINT `FK_monthly_invoice_users`
                                       FOREIGN KEY (`users_id`)
                                           REFERENCES `users` (`users_id`)
                                           ON UPDATE RESTRICT
                                           ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `subscribe_billing_history` (
                                             `subscribe_billing_history_id` BIGINT NOT NULL,
                                             `users_id` BIGINT NOT NULL,
                                             `device_id` BIGINT NOT NULL,
                                             `subscribe_service_id` INT NOT NULL,
                                             `service_name` VARCHAR(255) NOT NULL COMMENT '구독 상품명(기록 용도)',
                                             `subscription_start_date` DATE NOT NULL,
                                             `origin_amount` BIGINT NOT NULL,
                                             `discount_amount` BIGINT NOT NULL DEFAULT 0,
                                             `total_amount` BIGINT NOT NULL,
                                             `billing_yyyymm` INT NOT NULL COMMENT '6자리 정수(YYYYMM)',
                                             `created_at` DATETIME(6) NOT NULL,
                                             `expired_at` DATE NOT NULL COMMENT '생성일자 + 3년',
                                             PRIMARY KEY (`subscribe_billing_history_id`),
                                             CONSTRAINT `FK_subscribe_billing_history_users`
                                                 FOREIGN KEY (`users_id`)
                                                     REFERENCES `users` (`users_id`)
                                                     ON UPDATE RESTRICT
                                                     ON DELETE RESTRICT,
                                             CONSTRAINT `FK_subscribe_billing_history_device`
                                                 FOREIGN KEY (`device_id`)
                                                     REFERENCES `users_device` (`device_id`)
                                                     ON UPDATE RESTRICT
                                                     ON DELETE RESTRICT,
                                             CONSTRAINT `FK_subscribe_billing_history_service`
                                                 FOREIGN KEY (`subscribe_service_id`)
                                                     REFERENCES `subscribe_service` (`subscribe_service_id`)
                                                     ON UPDATE RESTRICT
                                                     ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `micro_payment_billing_history` (
                                                 `micro_payment_billing_history_id` BIGINT NOT NULL,
                                                 `users_id` BIGINT NOT NULL,
                                                 `micro_payment_service_id` INT NOT NULL,
                                                 `service_name` VARCHAR(255) NOT NULL,
                                                 `origin_amount` BIGINT NOT NULL,
                                                 `discount_amount` BIGINT NOT NULL DEFAULT 0,
                                                 `total_amount` BIGINT NOT NULL,
                                                 `billing_yyyymm` INT NOT NULL COMMENT '6자리 정수(YYYYMM)',
                                                 `created_at` DATETIME(6) NOT NULL,
                                                 `expired_at` DATE NOT NULL COMMENT '생성일자 + 3년',
                                                 PRIMARY KEY (`micro_payment_billing_history_id`),
                                                 CONSTRAINT `FK_micro_payment_billing_history_users`
                                                     FOREIGN KEY (`users_id`)
                                                         REFERENCES `users` (`users_id`)
                                                         ON UPDATE RESTRICT
                                                         ON DELETE RESTRICT,
                                                 CONSTRAINT `FK_micro_payment_billing_history_service`
                                                     FOREIGN KEY (`micro_payment_service_id`)
                                                         REFERENCES `micro_payment_service` (`micro_payment_service_id`)
                                                         ON UPDATE RESTRICT
                                                         ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `monthly_invoice_detail` (
                                          `detail_id` BIGINT NOT NULL,
                                          `invoice_id` BIGINT NOT NULL,
                                          `invoice_category_id` INT NOT NULL,
                                          `billing_history_id` BIGINT NOT NULL COMMENT '논리적 외래키 (polymorphic)',
                                          `service_name` VARCHAR(255) NOT NULL,
                                          `origin_amount` BIGINT NOT NULL,
                                          `discount_amount` BIGINT NOT NULL DEFAULT 0,
                                          `total_amount` BIGINT NOT NULL,
                                          `usage_start_date` DATETIME(6) NOT NULL,
                                          `usage_end_date` DATETIME(6) NOT NULL,
                                          `created_at` DATETIME(6) NOT NULL,
                                          `expired_at` DATE NOT NULL COMMENT '생성일자 + 5년',
                                          PRIMARY KEY (`detail_id`),
                                          CONSTRAINT `FK_monthly_invoice_detail_invoice`
                                              FOREIGN KEY (`invoice_id`)
                                                  REFERENCES `monthly_invoice` (`invoice_id`)
                                                  ON UPDATE RESTRICT
                                                  ON DELETE RESTRICT,
                                          CONSTRAINT `FK_monthly_invoice_detail_category`
                                              FOREIGN KEY (`invoice_category_id`)
                                                  REFERENCES `invoice_category` (`invoice_category_id`)
                                                  ON UPDATE RESTRICT
                                                  ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================
-- Delivery tables
-- =========================

CREATE TABLE `delivery_status` (
                                   `delivery_status_id` BIGINT NOT NULL,
                                   `invoice_id` BIGINT NOT NULL,
                                   `status` VARCHAR(20) NOT NULL,
                                   `delivery_channel` VARCHAR(20) NOT NULL,
                                   `retry_count` INT NOT NULL,
                                   `last_attempt_at` DATETIME(6) NOT NULL,
                                   `created_at` DATETIME(6) NOT NULL,
                                   PRIMARY KEY (`delivery_status_id`),
                                   CONSTRAINT `FK_delivery_status_invoice`
                                       FOREIGN KEY (`invoice_id`)
                                           REFERENCES `monthly_invoice` (`invoice_id`)
                                           ON UPDATE RESTRICT
                                           ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `delivery_history` (
                                    `delivery_history_id` BIGINT NOT NULL,
                                    `invoice_id` BIGINT NOT NULL,
                                    `attempt_no` INT NOT NULL,
                                    `delivery_channel` VARCHAR(20) NOT NULL,
                                    `receiver_info` VARCHAR(100) NOT NULL COMMENT '마스킹 처리',
                                    `status` VARCHAR(20) NOT NULL COMMENT 'SUCCESS/FAIL',
                                    `error_message` VARCHAR(255) NULL,
                                    `requested_at` DATETIME(6) NULL,
                                    `sent_at` DATETIME(6) NULL,
                                    PRIMARY KEY (`delivery_history_id`),
                                    CONSTRAINT `FK_delivery_history_invoice`
                                        FOREIGN KEY (`invoice_id`)
                                            REFERENCES `monthly_invoice` (`invoice_id`)
                                            ON UPDATE RESTRICT
                                            ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `delivery_summary` (
                                    `delivery_summary_id` BIGINT NOT NULL,
                                    `billing_yyyymm` INT NOT NULL COMMENT 'UK',
                                    `delivery_channel` VARCHAR(20) NOT NULL COMMENT 'UK',
                                    `total_attempt_count` INT NOT NULL DEFAULT 0,
                                    `success_count` INT NOT NULL DEFAULT 0,
                                    `fail_count` INT NOT NULL DEFAULT 0,
                                    `success_rate` DECIMAL(5,2) NULL,
                                    `created_at` DATETIME(6) NOT NULL,
                                    `updated_at` DATETIME(6) NULL,
                                    PRIMARY KEY (`delivery_summary_id`),
                                    UNIQUE KEY `UK_delivery_summary_yyyymm_channel` (`billing_yyyymm`, `delivery_channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =========================
-- Batch tables
-- =========================

CREATE TABLE `monthly_invoice_batch_attempt` (
                                                 `attempt_id` BIGINT NOT NULL,
                                                 `target_yyyymm` INT NOT NULL,
                                                 `execution_status` VARCHAR(20) NOT NULL,
                                                 `execution_type` VARCHAR(20) NOT NULL,
                                                 `started_at` DATETIME(6) NOT NULL,
                                                 `ended_at` DATETIME(6) NULL,
                                                 `duration_ms` BIGINT NULL,
                                                 `success_count` BIGINT NOT NULL DEFAULT 0,
                                                 `fail_count` BIGINT NOT NULL DEFAULT 0,
                                                 `host_name` VARCHAR(255) NOT NULL,
                                                 PRIMARY KEY (`attempt_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `monthly_invoice_batch_fail` (
                                              `fail_id` BIGINT NOT NULL,
                                              `attempt_id` BIGINT NOT NULL,
                                              `users_id` BIGINT NOT NULL,
                                              `target_yyyymm` INT NOT NULL,
                                              `error_code` VARCHAR(255) NOT NULL,
                                              `error_message` TEXT NULL,
                                              `created_at` DATETIME(6) NOT NULL,
                                              PRIMARY KEY (`fail_id`),
                                              CONSTRAINT `FK_monthly_invoice_batch_fail_attempt`
                                                  FOREIGN KEY (`attempt_id`)
                                                      REFERENCES `monthly_invoice_batch_attempt` (`attempt_id`)
                                                      ON UPDATE RESTRICT
                                                      ON DELETE RESTRICT,
                                              CONSTRAINT `FK_monthly_invoice_batch_fail_users`
                                                  FOREIGN KEY (`users_id`)
                                                      REFERENCES `users` (`users_id`)
                                                      ON UPDATE RESTRICT
                                                      ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;