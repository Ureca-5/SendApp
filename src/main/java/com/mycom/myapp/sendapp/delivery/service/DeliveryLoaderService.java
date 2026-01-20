package com.mycom.myapp.sendapp.delivery.service;

// 1. 상수 클래스 static import (Key 오타 방지)
import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.WAITING_STREAM;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

// 2. MapRecord 관련 import
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import com.mycom.myapp.sendapp.delivery.entity.DeliveryStatus;
import com.mycom.myapp.sendapp.delivery.entity.DeliveryUser;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryChannelType;
import com.mycom.myapp.sendapp.delivery.entity.enums.DeliveryStatusType;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryStatusRepository;
import com.mycom.myapp.sendapp.delivery.repository.DeliveryUserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryLoaderService {

    private final DeliveryStatusRepository deliveryStatusRepository;
    private final DeliveryUserRepository deliveryUserRepository;
    
    // 3. StringRedisTemplate 사용 (직렬화 이슈 원천 차단)
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * ✅ 메인 로직
     * 역할: 회원정보 조인 -> DB(배송상태) 중복 방지 저장 -> Redis(MapRecord) 적재
     */
    @Transactional
    public void loadChunk(List<MonthlyInvoiceRowDto> items) {
        
        // 1. [회원 정보 조회] - Bulk Select
        Set<Long> userIds = items.stream()
                .map(MonthlyInvoiceRowDto::getUsersId)
                .collect(Collectors.toSet());
        
        List<DeliveryUser> users = deliveryUserRepository.findAllUsersByIds(userIds); 

        Map<Long, DeliveryUser> userMap = users.stream()
                .collect(Collectors.toMap(DeliveryUser::getUserId, Function.identity()));


        // 2. [DB 작업] delivery_status 테이블 저장
        List<DeliveryStatus> statusList = items.stream()
                .map(item -> DeliveryStatus.builder()
                        .invoiceId(item.getInvoiceId())
                        .status(DeliveryStatusType.READY)
                        .deliveryChannel(DeliveryChannelType.EMAIL)
                        .retryCount(0)
                        .build())
                .collect(Collectors.toList());

        // 4. [DB 중복 방지] try-catch로 감싸서 한 건의 중복으로 전체 배치가 죽는 것을 방지
        try {
            deliveryStatusRepository.saveAllIgnore(statusList);
            log.info("✅ DB(delivery_status) 저장 완료: {}건", items.size());
        } catch (Exception e) {
            // DuplicateKeyException 등을 잡아서 로그만 남기고 진행 (혹은 개별 Insert 로직으로 Fallback)
            log.warn("⚠️ DB 저장 중 중복 데이터 존재 가능성 있음 (무시하고 진행): {}", e.getMessage());
        }


        // 3. [Redis 작업] Pipelined를 통한 대량 적재
        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (MonthlyInvoiceRowDto item : items) {
                
                DeliveryUser user = userMap.get(item.getUsersId());

                if (user == null) {
                    log.warn("🚨 회원 정보 없음 (Skip) - InvoiceId: {}", item.getInvoiceId());
                    continue; 
                }
                
                // 5. [데이터 변환] Worker가 요구하는 평문 Map 생성
                Map<String, String> streamMap = new HashMap<>();
                
                // (A) Worker 제어용 필수 필드 (Worker 코드와 Key 일치시킴)
                streamMap.put("invoice_id", String.valueOf(item.getInvoiceId()));
                streamMap.put("delivery_channel", "EMAIL");
                streamMap.put("retry_count", "0");
                streamMap.put("receiver_info", user.getEmail()); // Worker가 'receiver_info'로 꺼냄

                // (B) 실제 발송(이메일 본문)에 필요한 추가 정보들
                streamMap.put("recipient_name", user.getName());
                streamMap.put("billing_yyyymm", formatYyyymm(item.getBillingYyyymm()));
                streamMap.put("total_amount", formatMoney(item.getTotalAmount()));
                // 필요시 더 많은 필드 추가 가능 (MapRecord라 유연함)

                // 6. [MapRecord 생성]
                MapRecord<String, String, String> record = StreamRecords.newRecord()
                        .in(WAITING_STREAM) // 상수로 관리되는 Key
                        .ofMap(streamMap);  // Map 그대로 넣음

                // StringRedisTemplate의 connection을 사용하여 추가
                stringRedisTemplate.opsForStream().add(record);
            }
            return null;
        });
        
        log.info("✅ Redis Stream 적재 완료 (Key: {}): {}건", WAITING_STREAM, items.size());
    }


    // ────────────────────────────────────────────────────────────────
    // Format Helpers
    // ────────────────────────────────────────────────────────────────
    
    private String formatDate(TemporalAccessor date) {
        if (date == null) return "";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date);
    }

    private String formatMoney(Long amount) {
        if (amount == null) return "0";
        return new DecimalFormat("#,###").format(amount);
    }

    private String formatYyyymm(Integer yyyymm) {
        if (yyyymm == null) return "";
        String s = String.valueOf(yyyymm);
        if (s.length() != 6) return s;
        return s.substring(0, 4) + "년 " + s.substring(4, 6) + "월";
    } 
}