package com.mycom.myapp.sendapp.delivery.service;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.DELAY_ZSET;

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

import org.redisson.api.RBatch;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    
    // Redisson & Jackson (배치 성능 최적화용)
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    // ⛔ [Code B에서 가져옴] 시스템 발송 금지 시간 설정 (21:00 ~ 09:00)
    private static final int BAN_START_HOUR = 21; 
    private static final int BAN_END_HOUR = 9;    

    public void loadChunk(List<MonthlyInvoiceRowDto> items) {
        
        // 1. [회원 정보 조회]
        Set<Long> userIds = items.stream()
                .map(MonthlyInvoiceRowDto::getUsersId)
                .collect(Collectors.toSet());
        
        List<DeliveryUser> users = deliveryUserRepository.findAllUsersByIds(userIds); 

        Map<Long, DeliveryUser> userMap = users.stream()
                .collect(Collectors.toMap(DeliveryUser::getUsersId, Function.identity()));


        // 2. [데이터 분류]
        List<DeliveryStatus> statusList = new ArrayList<>();
        List<MonthlyInvoiceRowDto> immediatePushItems = new ArrayList<>(); 

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
            // ★ [병합됨] 날짜 예약 + 야간 제한 로직
            // ──────────────────────────────────────────────
            boolean isReservation = false;
            LocalDateTime scheduledTime = null; // DB에 저장할 최종 시간

            // 1) 기본 목표 시간은 '현재(즉시)'
            LocalDateTime targetTime = now; 
            
            Integer pDay = user.getPreferredDay();
            Integer pHour = user.getPreferredHour();

            // 2) 유저 설정이 있으면 targetTime 변경
            if (pDay != null && pHour != null) {
                try {
                    int lastDayOfMonth = YearMonth.of(currentYear, currentMonth).lengthOfMonth();
                    int targetDay = Math.min(pDay, lastDayOfMonth); 
                    targetTime = LocalDateTime.of(currentYear, currentMonth, targetDay, pHour, 0);
                } catch (Exception e) {
                    targetTime = now; 
                }
            }
            
            // 3) ★ [Code B 적용] 금지 시간대(야간) 체크 및 보정
            targetTime = adjustForBusinessHours(targetTime);

            // 4) 미래인지 확인
            if (targetTime.isAfter(now)) {
                isReservation = true;
                scheduledTime = targetTime; // DB에 박제할 예약 시간
            }

            // 3. [DB 엔티티 생성]
            DeliveryStatus status = DeliveryStatus.builder()
                    .invoiceId(item.getInvoiceId())
                    .status(isReservation ? DeliveryStatusType.SCHEDULED : DeliveryStatusType.READY)
                    .scheduledAt(scheduledTime) 
                    .deliveryChannel(DeliveryChannelType.EMAIL)
                    .retryCount(0)
                    .build();
            
            statusList.add(status);

            // 4. [Redis 대상 선별] 예약이 '아닌' 경우만 즉시 발송
            if (!isReservation) {
                immediatePushItems.add(item);
            }
        }

        // 5. [DB 저장]
        saveDeliveryStatus(statusList);
        

        // 6. [Redis 작업 - Code A (Redisson Batch) 사용]
        if (!immediatePushItems.isEmpty()) {
            try {
            	// Redisson Batch 시작 (네트워크 왕복 최소화)
            	RBatch batch = redissonClient.createBatch();
            	RScoredSortedSetAsync<String> batchZset = batch.getScoredSortedSet(DELAY_ZSET, StringCodec.INSTANCE);
            	
            	// 약간의 지연(1초)을 주어 컨슈머가 DB 커밋 후 읽어가도록 유도
            	long delayUntil = System.currentTimeMillis() + 1000; 
            	
            	for (MonthlyInvoiceRowDto item : immediatePushItems) {
                  DeliveryUser user = userMap.get(item.getUsersId());
                  
                  Map<String, String> payload = new HashMap<>();
                  payload.put("invoice_id", String.valueOf(item.getInvoiceId()));
                  payload.put("delivery_channel", "EMAIL");
                  payload.put("retry_count", "0");
                  payload.put("email", user.getEmail()); 
                  payload.put("phone", user.getPhone()); 
                  payload.put("recipient_name", user.getName());
                  payload.put("billing_yyyymm", formatYyyymm(item.getBillingYyyymm()));
                  payload.put("requested_at", currentRequestTime);
                  
                  // 금액 관련 필드들
                  payload.put("totalPlanAmount", formatMoney(item.getTotalPlanAmount()));
                  payload.put("totalAddonAmount", formatMoney(item.getTotalAddonAmount()));
                  payload.put("totalEtcAmount", formatMoney(item.getTotalEtcAmount()));
                  payload.put("totalDiscountAmount", formatMoney(item.getTotalDiscountAmount()));
                  payload.put("total_amount", formatMoney(item.getTotalAmount()));
                  payload.put("dueDate", formatDate(item.getDueDate()));
                  
                  try {
                      String jsonPayload = objectMapper.writeValueAsString(payload);
                      // 비동기로 배치에 추가
                      batchZset.addAsync(delayUntil, jsonPayload);
                  } catch (JsonProcessingException e) {
                      log.error("JSON 직렬화 실패 - InvoiceId: {}, Error: {}", item.getInvoiceId(), e.getMessage());
                  }
                }
            	
            	// 배치 일괄 실행
            	batch.execute();
            	
                log.info("✅ Redis Batch 적재 완료 (즉시 발송): {}건 / 예약 대기: {}건", 
                        immediatePushItems.size(), items.size() - immediatePushItems.size());
                
            } catch (Exception e) {
                log.error("🚨 Redis 적재 실패 (DB는 성공함): {}", e.getMessage());
            }
        } else {
            log.info("⏳ 모든 건이 예약 대상이므로 Redis 적재 생략 (DB 저장 완료)");
        }
    }

    /**
     * 🕒 [Code B에서 가져옴] 금지 시간대면 업무 시간(09:00)으로 미루는 로직
     */
    private LocalDateTime adjustForBusinessHours(LocalDateTime targetTime) {
        int hour = targetTime.getHour();

        // 21시 ~ 09시 사이면 -> 09시로 이동
        if (hour >= BAN_START_HOUR) {
            return targetTime.plusDays(1).withHour(BAN_END_HOUR).withMinute(0).withSecond(0);
        }
        if (hour < BAN_END_HOUR) {
            return targetTime.withHour(BAN_END_HOUR).withMinute(0).withSecond(0);
        }
        return targetTime;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveDeliveryStatus(List<DeliveryStatus> statusList) {
        try {
            deliveryStatusRepository.saveAllIgnore(statusList);
            log.info("DB 저장 완료: {}건", statusList.size());
        } catch (Exception e) {
            log.warn("DB 중복 무시: {}", e.getMessage());
        }
    }

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