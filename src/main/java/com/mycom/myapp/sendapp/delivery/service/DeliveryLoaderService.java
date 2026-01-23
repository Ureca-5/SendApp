package com.mycom.myapp.sendapp.delivery.service;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.WAITING_STREAM;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Propagation;
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
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * ✅ 메인 로직 (하이브리드 적재 구현 - 날짜 예약 포함)
     * 1. 트랜잭션 제거 (DB Lock 방지)
     * 2. 예약 발송 여부 판단 (날짜+시간) -> DB에는 SCHEDULED 저장, Redis 적재는 스킵
     * 3. 즉시 발송 건만 Redis Pipeline 태움
     */
    public void loadChunk(List<MonthlyInvoiceRowDto> items) {
        
        // 1. [회원 정보 조회]
        Set<Long> userIds = items.stream()
                .map(MonthlyInvoiceRowDto::getUsersId)
                .collect(Collectors.toSet());
        
        List<DeliveryUser> users = deliveryUserRepository.findAllUsersByIds(userIds); 

        Map<Long, DeliveryUser> userMap = users.stream()
                .collect(Collectors.toMap(DeliveryUser::getUserId, Function.identity()));


        // 2. [데이터 분류]
        List<DeliveryStatus> statusList = new ArrayList<>();
        List<MonthlyInvoiceRowDto> immediatePushItems = new ArrayList<>(); 

        // 기준 시간 (현재)
        LocalDateTime now = LocalDateTime.now();
        String currentRequestTime = now.toString();
        
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        for (MonthlyInvoiceRowDto item : items) {
            DeliveryUser user = userMap.get(item.getUsersId());
            if (user == null) {
                log.warn("🚨 회원 정보 없음 (Skip) - InvoiceId: {}", item.getInvoiceId());
                continue; 
            }

            // ──────────────────────────────────────────────
            // ★ [핵심 수정] 날짜 + 시간 예약 판단 로직
            // ──────────────────────────────────────────────
            boolean isReservation = false;
            LocalDateTime scheduledTime = null;
            
            Integer pDay = user.getPreferredDay();   // 유저가 원하는 날짜 (1~31)
            Integer pHour = user.getPreferredHour(); // 유저가 원하는 시간 (0~23)

            // 날짜와 시간이 모두 설정된 경우에만 예약 로직 수행
            if (pDay != null && pHour != null) {
                try {
                    // (1) 이번 달의 마지막 날짜 구하기 (예: 2월은 28일, 1월은 31일)
                    int lastDayOfMonth = YearMonth.of(currentYear, currentMonth).lengthOfMonth();
                    
                    // (2) 유저가 설정한 날짜가 마지막 날짜보다 크면 보정 (예: 31일 설정했는데 2월이면 28일로)
                    int targetDay = Math.min(pDay, lastDayOfMonth); 
                    
                    // (3) 목표 시간 생성: 금년 금월 [targetDay]일 [pHour]시 0분 0초
                    LocalDateTime targetTime = LocalDateTime.of(currentYear, currentMonth, targetDay, pHour, 0);
                    
                    // (4) 미래인지 확인 (과거면 즉시 발송)
                    if (targetTime.isAfter(now)) {
                        isReservation = true;
                        scheduledTime = targetTime;
                    }
                } catch (Exception e) {
                    log.warn("날짜 계산 오류 (User: {}) - 즉시 발송 처리", user.getUserId());
                }
            }
            // (참고: 날짜 없이 시간만 있는 경우는 제외했습니다. 필요시 else if 추가 가능)

            // 3. [DB 엔티티 생성]
            DeliveryStatus status = DeliveryStatus.builder()
                    .invoiceId(item.getInvoiceId())
                    // 예약이면 SCHEDULED, 아니면 READY
                    .status(isReservation ? DeliveryStatusType.SCHEDULED : DeliveryStatusType.READY)
                    .scheduledAt(scheduledTime) // 계산된 예약 시간 저장
                    .deliveryChannel(DeliveryChannelType.EMAIL)
                    .retryCount(0)
                    .build();
            
            statusList.add(status);

            // 4. [Redis 대상 선별] 예약이 '아닌' 경우만 즉시 발송
            if (!isReservation) {
                immediatePushItems.add(item);
            }
        }

        // 5. [DB 저장] 별도 트랜잭션
        saveDeliveryStatus(statusList);
        

        // 6. [Redis 작업] 즉시 발송 대상만 처리
        if (!immediatePushItems.isEmpty()) {
            try {
                stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                    for (MonthlyInvoiceRowDto item : immediatePushItems) {
                        
                        DeliveryUser user = userMap.get(item.getUsersId());
                        
                        Map<String, String> streamMap = new HashMap<>();
                        streamMap.put("invoice_id", String.valueOf(item.getInvoiceId()));
                        streamMap.put("delivery_channel", "EMAIL");
                        streamMap.put("retry_count", "0");
                        streamMap.put("email", user.getEmail()); 
                        streamMap.put("phone", user.getPhone()); 
                        streamMap.put("recipient_name", user.getName());
                        streamMap.put("billing_yyyymm", formatYyyymm(item.getBillingYyyymm()));
                        streamMap.put("total_amount", formatMoney(item.getTotalAmount()));
                        streamMap.put("requested_at", currentRequestTime);
                        
                        MapRecord<String, String, String> record = StreamRecords.newRecord()
                                .in(WAITING_STREAM)
                                .ofMap(streamMap);

                        stringRedisTemplate.opsForStream().add(record);
                    }
                    return null;
                });
                log.info("✅ Redis Stream 적재 완료 (즉시 발송): {}건 / 예약 대기: {}건", 
                        immediatePushItems.size(), items.size() - immediatePushItems.size());
                
            } catch (Exception e) {
                log.error("🚨 Redis 적재 실패 (DB는 성공함): {}", e.getMessage());
            }
        } else {
            log.info("⏳ 모든 건이 예약 대상이므로 Redis 적재 생략 (DB 저장 완료)");
        }
    }

    // DB 저장 전용 (트랜잭션 분리)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveDeliveryStatus(List<DeliveryStatus> statusList) {
        try {
            deliveryStatusRepository.saveAllIgnore(statusList);
            log.info("DB(delivery_status) 저장 완료: {}건", statusList.size());
        } catch (Exception e) {
            log.warn("DB 저장 중 중복 데이터 존재 가능성 있음 (무시하고 진행): {}", e.getMessage());
        }
    }

    // Format Helpers
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