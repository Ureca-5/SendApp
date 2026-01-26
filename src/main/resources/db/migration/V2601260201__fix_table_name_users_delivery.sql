/* * [FIX] 테이블 명명 규칙 통일 (user_delivery_settings -> users_delivery_settings)
 * - 기존 V2601232301에서 생성한 잘못된 이름의 테이블 제거
 * - users_id 컬럼명을 사용하는 올바른 테이블 생성
 */

-- 1. 기존(잘못된) 테이블 삭제 
DROP TABLE IF EXISTS user_delivery_settings;

-- 2. 올바른 이름(users_delivery_settings)과 컬럼(users_id)으로 재생성
CREATE TABLE users_delivery_settings (
    users_id BIGINT NOT NULL,
    preferred_hour INT NOT NULL COMMENT '선호 배송 시간(0~23)',
    preferred_day INT NOT NULL DEFAULT 3 COMMENT '매월 선호 배송일(1~31)', 
    PRIMARY KEY (users_id)
);