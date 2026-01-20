package com.mycom.myapp.sendapp.delivery.service;

import static com.mycom.myapp.sendapp.delivery.config.DeliveryRedisKey.*;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.sendapp.batch.dto.MonthlyInvoiceRowDto;
import com.mycom.myapp.sendapp.delivery.dto.DeliveryRequestDto;
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
    private final RedisTemplate<String, Object> redisTemplate;
    private final DeliveryUserRepository deliveryUserRepository;
    private final StringRedisTemplate stringRedisTemplate;

    // Redis 키 상수 -> 상수 클래스 사용
//    private static final String WAITING_QUEUE_KEY = "billing:delivery:waiting";

    /**
     * ✅ 메인 로직
     * 입력: DTO 리스트 (MonthlyInvoiceRowDto)
     * 역할: 회원정보 조인 -> DB(배송상태) 저장 -> Redis(대기열) 적재
     */
    @Transactional
    public void loadChunk(List<MonthlyInvoiceRowDto> items) {
        
        // 1. [회원 정보 조회]
        Set<Long> userIds = items.stream()
                .map(MonthlyInvoiceRowDto::getUsersId)
                .collect(Collectors.toSet());
        
        // 1-2. DB(users 테이블)에 딱 1번만 가서 모든 유저 정보를 가져옴 (Bulk Select)
        List<DeliveryUser> users = deliveryUserRepository.findAllUsersByIds(userIds); 

        // Map 변환
        Map<Long, DeliveryUser> userMap = users.stream()
                .collect(Collectors.toMap(DeliveryUser::getUserId, Function.identity()));


        // 2. [DB 작업] delivery_status 테이블에 'READY' 저장
        List<DeliveryStatus> statusList = items.stream()
                .map(item -> DeliveryStatus.builder()
                        .invoiceId(item.getInvoiceId())
                        .status(DeliveryStatusType.READY)
                        .deliveryChannel(DeliveryChannelType.EMAIL)
                        .retryCount(0)
                        .build())
                .collect(Collectors.toList());

        deliveryStatusRepository.saveAll(statusList);
        log.info("✅ DB(delivery_status) 저장 완료: {}건", items.size());


        // 3. [Redis 작업] User 정보 합쳐서 Stream 적재
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (MonthlyInvoiceRowDto item : items) {
                
                DeliveryUser user = userMap.get(item.getUsersId());

                if (user == null) {
                    log.warn("🚨 회원 정보 없음 (Skip) - InvoiceId: {}", item.getInvoiceId());
                    continue; 
                }
                
                // ==================================================//
                // objectRecord 사용 시 직렬화 문제 발생함.
                // Redis 전송용 DTO 변환
                DeliveryRequestDto redisDto = convertToRedisDto(item, user);

                // 레코드 생성
                ObjectRecord<String, DeliveryRequestDto> record = StreamRecords.newRecord()
                        .ofObject(redisDto)
                        .withStreamKey(WAITING_STREAM);
                
                // =====================================================//
                
                stringRedisTemplate.opsForStream().add(record);
            }
            return null;
        });
        
        log.info("✅ Redis Stream 적재 완료 (Key: {}): {}건", WAITING_STREAM, items.size());
    }

    // ────────────────────────────────────────────────────────────────
    // 변환 메서드 (Converter)
    // ────────────────────────────────────────────────────────────────

    private DeliveryRequestDto convertToRedisDto(MonthlyInvoiceRowDto item, DeliveryUser user) {
        return DeliveryRequestDto.builder()
                .eventType("BILLING_CREATED")
                .invoiceId(item.getInvoiceId())
                .recipient(DeliveryRequestDto.Recipient.builder()
                        .name(user.getName())
                        .email(user.getEmail())
                        .phone(user.getPhone()) 
                        .build())
                .summary(DeliveryRequestDto.BillSummary.builder()
                        .billingYyyymm(formatYyyymm(item.getBillingYyyymm()))
                        .issueDate(formatDate(item.getCreatedAt()))
                        .totalAmount(formatMoney(item.getTotalAmount()))
                        .planAmount(formatMoney(item.getTotalPlanAmount()))
                        .addonAmount(formatMoney(item.getTotalAddonAmount()))
                        .etcAmount(formatMoney(item.getTotalEtcAmount()))
                        .discountAmount(formatMoney(item.getTotalDiscountAmount()))
                        .build())
                .build();
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