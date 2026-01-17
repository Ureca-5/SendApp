package com.mycom.myapp.sendapp.batch.support;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;

/**
 * 운영환경용 기본 구현체.
 *
 * - hostname:pid 형태로 반환합니다.
 * - hostname 조회 실패 시 unknown-host로 대체합니다.
 * - pid 조회 실패 시 unknown-pid로 대체합니다.
 */
@Component
public class DefaultHostIdentifier implements HostIdentifier {
    @Override
    public String get() {
        String host = resolveHostName();
        String pid = resolvePid();
        return host + ":" + pid;
    }
    private String resolveHostName() {
        try {
            // 일반적으로 mac/linux/container에서 hostname을 얻는 가장 무난한 방식
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-host";
        }
    }

    private String resolvePid() {
        try {
            // "pid@hostname" 형태로 나오는 경우가 많음
            String name = ManagementFactory.getRuntimeMXBean().getName();
            int at = name.indexOf('@');
            if (at > 0) {
                return name.substring(0, at);
            }
            return name;
        } catch (Exception e) {
            return "unknown-pid";
        }
    }
}
