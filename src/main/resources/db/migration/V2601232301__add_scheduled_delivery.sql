/* * [FEAT] 예약 발송 시스템 도입
 * 1. 유저 설정 테이블(user_delivery_settings) 생성
 * 2. 배송 상태 테이블(delivery_status)에 예약 시간 컬럼 추가
 */

-- 1. 유저 선호 시간 설정 테이블 생성
CREATE TABLE user_delivery_settings (
    user_id BIGINT NOT NULL,
    preferred_hour INT NOT NULL COMMENT '선호 배송 시간(0~23)',
    -- (사용자가 날짜를 안 넣으면 자동으로 매월 4일로 설정됨)
    preferred_day INT NOT NULL DEFAULT 4 COMMENT '매월 선호 배송일(1~31)', 
    PRIMARY KEY (user_id)
);

-- 2. 배송 상태 테이블에 '예약 발송 시간' 컬럼 추가
ALTER TABLE delivery_status 
ADD COLUMN scheduled_at DATETIME NULL COMMENT '실제 예약 발송 예정 시각';

-- 3. 스케줄러 성능을 위한 인덱스 (필수)
CREATE INDEX idx_delivery_status_schedule ON delivery_status (status, scheduled_at);