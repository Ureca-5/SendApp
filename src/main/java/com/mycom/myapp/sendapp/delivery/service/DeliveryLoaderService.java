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
	
	public static int currentProcessingYyyymm = 0;
	
    private final DeliveryStatusRepository deliveryStatusRepository;
    private final DeliveryUserRepository deliveryUserRepository;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    
    private static final int BAN_START_HOUR = 21; 
    private static final int BAN_END_HOUR = 9;    

    public void loadChunk(List<MonthlyInvoiceRowDto> items) {
        // 1. 회원 정보 조회
        Set<Long> userIds = items.stream().map(MonthlyInvoiceRowDto::getUsersId).collect(Collectors.toSet());
        List<DeliveryUser> users = deliveryUserRepository.findAllUsersByIds(userIds); 
        Map<Long, DeliveryUser> userMap = users.stream().collect(Collectors.toMap(DeliveryUser::getUsersId, Function.identity()));

        List<DeliveryStatus> statusList = new ArrayList<>();
        List<MonthlyInvoiceRowDto> immediatePushItems = new ArrayList<>(); 

        LocalDateTime now = LocalDateTime.now();
        String currentRequestTime = now.toString();
        int currentYear = now.getYear();
        int currentMonth = now.getMonthValue();

        for (MonthlyInvoiceRowDto item : items) {
            DeliveryUser user = userMap.get(item.getUsersId());
            if (user == null) continue; 

            // ★ 날짜 예약 + 야간 제한 로직
            boolean isReservation = false;
            LocalDateTime scheduledTime = null;
            LocalDateTime targetTime = now; 
            
            Integer pDay = user.getPreferredDay();
            Integer pHour = user.getPreferredHour();

            if (pDay != null && pHour != null) {
                try {
                    int lastDayOfMonth = YearMonth.of(currentYear, currentMonth).lengthOfMonth();
                    int targetDay = Math.min(pDay, lastDayOfMonth); 
                    targetTime = LocalDateTime.of(currentYear, currentMonth, targetDay, pHour, 0);
                } catch (Exception e) { targetTime = now; }
            }
            
            // ★ 금지 시간대(야간) 체크 및 보정
            targetTime = adjustForBusinessHours(targetTime);

            if (targetTime.isAfter(now)) {
                isReservation = true;
                scheduledTime = targetTime;
            }

            statusList.add(DeliveryStatus.builder()
                    .invoiceId(item.getInvoiceId())
                    .status(isReservation ? DeliveryStatusType.SCHEDULED : DeliveryStatusType.READY)
                    .scheduledAt(scheduledTime) 
                    .deliveryChannel(DeliveryChannelType.EMAIL)
                    .retryCount(0)
                    .build());

            if (!isReservation) {
                immediatePushItems.add(item);
            }
        }

        // DB 저장 (Batch)
        saveDeliveryStatus(statusList);
        
        
        // Redis 작업 (Redisson Batch)
        if (!immediatePushItems.isEmpty()) {
            try {
                RBatch batch = redissonClient.createBatch();
                RScoredSortedSetAsync<String> batchZset = batch.getScoredSortedSet(DELAY_ZSET, StringCodec.INSTANCE);
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
                  payload.put("total_amount", formatMoney(item.getTotalAmount()));
                  payload.put("dueDate", formatDate(item.getDueDate()));
                  
                  currentProcessingYyyymm = item.getBillingYyyymm();
                  
                  try {
                      String json = objectMapper.writeValueAsString(payload);
                      batchZset.addAsync(delayUntil, json);
                  } catch (JsonProcessingException e) {
                      log.error("JSON Error: {}", e.getMessage());
                  }
                }
                batch.execute();
                log.info("✅ Loader: {}건 Redis Batch 적재 완료", immediatePushItems.size());
            } catch (Exception e) {
                log.error("🚨 Redis 적재 실패: {}", e.getMessage());
            }
        }
    }

    private LocalDateTime adjustForBusinessHours(LocalDateTime targetTime) {
        int hour = targetTime.getHour();
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
        } catch (Exception e) {
            log.warn("DB 중복 무시: {}", e.getMessage());
        }
    }

    private String formatDate(TemporalAccessor date) { return date == null ? "" : DateTimeFormatter.ofPattern("yyyy-MM-dd").format(date); }
    private String formatMoney(Long amount) { return amount == null ? "0" : new DecimalFormat("#,###").format(amount); }
    private String formatYyyymm(Integer yyyymm) {
        if (yyyymm == null) return "";
        String s = String.valueOf(yyyymm);
        return s.length() == 6 ? s.substring(0, 4) + "년 " + s.substring(4, 6) + "월" : s;
    } 
}