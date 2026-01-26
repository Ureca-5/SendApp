-- １. 발송 이력 테이블에 연월 컬럼 추가
ALTER TABLE delivery_history 
ADD COLUMN billing_yyyymm INT NOT NULL COMMENT '대상 연월 (YYYYMM)' AFTER invoice_id;

-- ２. 통계 요약 테이블에 복합 Unique Key 추가 (중복 방지 및 ON DUPLICATE KEY UPDATE용)
ALTER TABLE delivery_summary 
ADD UNIQUE KEY uk_billing_channel (billing_yyyymm, delivery_channel);