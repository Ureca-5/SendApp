package com.mycom.myapp.sendapp.batch.support;

/**
 * 배치 실행 주체(호스트/프로세스/인스턴스)를 식별하기 위한 추상화.
 *
 * 개발 근거:
 * - attempt.host_name 같은 컬럼에 값을 넣을 때, 호출부가 OS/컨테이너 환경에 직접 의존하지 않게 하기 위함입니다.
 * - 추후 다중 서버/락 테이블 도입 시 locked_by 값으로 재사용 가능합니다.
 */
public interface HostIdentifier {
    /**
     * 사람/운영자가 로그/DB에서 봤을 때 유의미한 식별자 문자열을 반환합니다.
     * 예: hostname:pid
     */
    String get();
}
