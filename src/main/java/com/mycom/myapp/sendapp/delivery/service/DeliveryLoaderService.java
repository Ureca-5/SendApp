package com.mycom.myapp.sendapp.delivery.service;

// 1. 상수 클래스 static import
import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.WAITING_STREAM;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation; // 추가됨
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
    
    // StringRedisTemplate 사용
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * ✅ 메인 로직
     * 수정: @Transactional 제거, DB 저장 함수 분리
     */
    // ❌ 여기에 @Transactional을 걸면 안 됩니다! (Redis 타임아웃 동안 DB Lock이 유지됨)
    public void loadChunk(List<MonthlyInvoiceRowDto> items) {
        
        // 1. [회원 정보 조회] - Bulk Select
        Set<Long> userIds = items.stream()
                .map(MonthlyInvoiceRowDto::getUsersId)
                .collect(Collectors.toSet());
        
        List<DeliveryUser> users = deliveryUserRepository.findAllUsersByIds(userIds); 

        Map<Long, DeliveryUser> userMap = users.stream()
                .collect(Collectors.toMap(DeliveryUser::getUserId, Function.identity()));


        // 2. [데이터 준비] delivery_status 엔티티 리스트 생성
        List<DeliveryStatus> statusList = items.stream()
                .map(item -> DeliveryStatus.builder()
                        .invoiceId(item.getInvoiceId())
                        .status(DeliveryStatusType.READY)
                        .deliveryChannel(DeliveryChannelType.EMAIL)
                        .retryCount(0)
                        .build())
                .collect(Collectors.toList());

        // 3. [DB 저장] ★ 함수 분리 (여기서 트랜잭션이 시작되고 끝남 -> Lock 해제)
        saveDeliveryStatus(statusList);
        
        // 시간 기록 (DB 저장 직후 시점)
        String finalRequestedAt = LocalDateTime.now().toString();


        // 4. [Redis 작업] Pipelined를 통한 대량 적재 (이제 DB Lock 걱정 없이 수행 가능)
        final String timeForRedis = finalRequestedAt;
        
        try {
            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (MonthlyInvoiceRowDto item : items) {
                    
                    DeliveryUser user = userMap.get(item.getUsersId());
                    if (user == null) {
                        log.warn("🚨 회원 정보 없음 (Skip) - InvoiceId: {}", item.getInvoiceId());
                        continue; 
                    }
                    
                    // 5. [데이터 변환] 기존 로직 유지 (Map 직접 사용)
                    Map<String, String> streamMap = new HashMap<>();
                    
                    // (A) Worker 제어용 필수 필드
                    streamMap.put("invoice_id", String.valueOf(item.getInvoiceId()));
                    streamMap.put("delivery_channel", "EMAIL");
                    streamMap.put("retry_count", "0");
                    streamMap.put("email", user.getEmail()); 
                    streamMap.put("phone", user.getPhone()); 
                    
                    // (B) 실제 발송 정보
                    streamMap.put("recipient_name", user.getName());
                    streamMap.put("billing_yyyymm", formatYyyymm(item.getBillingYyyymm()));
                    streamMap.put("total_amount", formatMoney(item.getTotalAmount()));
                    streamMap.put("requested_at", timeForRedis);
                    
                    // 6. [MapRecord 생성]
                    MapRecord<String, String, String> record = StreamRecords.newRecord()
                            .in(WAITING_STREAM)
                            .ofMap(streamMap);

                    stringRedisTemplate.opsForStream().add(record);
                }
                return null;
            });
            log.info("✅ Redis Stream 적재 완료 (Key: {}): {}건", WAITING_STREAM, items.size());
            
        } catch (Exception e) {
            // DB에는 이미 저장이 완료된 상태이므로, Redis 실패 로그만 남김 (데이터 유실 아님, 재처리 가능)
            log.error("🚨 Redis 적재 실패 (DB 저장은 성공함): {}", e.getMessage());
        }
    }


    // ────────────────────────────────────────────────────────────────
    // ★ [핵심 수정] DB 저장 전용 메서드 분리
    // 트랜잭션을 새로 열고(REQUIRES_NEW), 끝나면 즉시 커밋해서 Lock을 풉니다.
    // ────────────────────────────────────────────────────────────────
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveDeliveryStatus(List<DeliveryStatus> statusList) {
        try {
            deliveryStatusRepository.saveAllIgnore(statusList);
            log.info("DB(delivery_status) 저장 완료: {}건", statusList.size());
        } catch (Exception e) {
            log.warn("DB 저장 중 중복 데이터 존재 가능성 있음 (무시하고 진행): {}", e.getMessage());
        }
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