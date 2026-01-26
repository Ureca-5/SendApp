-- settlement_status_history 테이블의 pk 생성 전략을 'auto_increment'로 설정

ALTER TABLE settlement_status_history
    MODIFY COLUMN settlement_status_history_id BIGINT not null AUTO_INCREMENT;