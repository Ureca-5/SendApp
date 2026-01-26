package com.mycom.myapp.sendapp.admin.user.service;

import com.mycom.myapp.sendapp.admin.user.dao.UserDao;
import com.mycom.myapp.sendapp.admin.user.dto.UserRowRawDTO;
import com.mycom.myapp.sendapp.admin.user.dto.UserRowViewDTO;
import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    // users.html은 Page Size 20 고정 :contentReference[oaicite:6]{index=6}
    public static final int FIXED_SIZE = 20;

    // 이름으로 후보가 아주 많을 때도 email/phone으로 찾을 수 있게 "최대 N페이지"까지 스캔
    private static final int MAX_SCAN_PAGES = 50; // 20*50 = 1000 rows (name 필터가 걸려있는 전제)

    private final UserDao userDao;
    private final ContactProtector protector;

    public UserService(UserDao userDao, ContactProtector protector) {
        this.userDao = userDao;
        this.protector = protector;
    }

    public List<UserRowViewDTO> list(boolean searched,
                                    String keyword,
                                    String emailFilter,
                                    String phoneFilter,
                                    Boolean withdrawn,
                                    int page) {
        if (!searched) return List.of();

        boolean hasExtraFilter = hasText(emailFilter) || hasText(phoneFilter);

        // email/phone 필터만 단독으로는 지원하지 않음: 암호문 저장이라 DB에서 못 좁힘(전체 스캔 위험)
        if (hasExtraFilter && !hasText(keyword)) return List.of();

        if (!hasExtraFilter) {
            // 기존 방식: DB에서 page 단위로 가져오고 화면에 뿌림
            int safePage = Math.max(page, 0);
            int offset = safePage * FIXED_SIZE;

            List<UserRowRawDTO> raws = userDao.findUsers(keyword, withdrawn, FIXED_SIZE, offset);
            return raws.stream().map(this::toView).toList();
        }

        // email/phone 필터가 있으면: "이름 후보군"을 여러 페이지 스캔해서 특정
        List<UserRowViewDTO> matches = new ArrayList<>();
        for (int scanPage = 0; scanPage < MAX_SCAN_PAGES; scanPage++) {
            int offset = scanPage * FIXED_SIZE;
            List<UserRowRawDTO> raws = userDao.findUsers(keyword, withdrawn, FIXED_SIZE, offset);
            if (raws.isEmpty()) break;

            for (UserRowRawDTO r : raws) {
                UserRowViewDTO v = toView(r);
                if (matchExtraFilters(v, emailFilter, phoneFilter)) {
                    matches.add(v);
                }
            }
        }

        // 스캔된 매칭 결과에 대해 페이징 적용
        int safePage = Math.max(page, 0);
        int from = safePage * FIXED_SIZE;
        if (from >= matches.size()) return List.of();
        int to = Math.min(from + FIXED_SIZE, matches.size());
        return matches.subList(from, to);
    }

    public int countIfNeeded(String keyword,
                             String emailFilter,
                             String phoneFilter,
                             Boolean withdrawn) {
        if (!hasText(keyword)) return -1;

        boolean hasExtraFilter = hasText(emailFilter) || hasText(phoneFilter);
        if (!hasExtraFilter) {
            // 기존 정책: keyword 있을 때만 count :contentReference[oaicite:7]{index=7}
            return userDao.countUsers(keyword, withdrawn);
        }

        // email/phone 필터가 있는 경우:
        // 정확 count를 DB에서 못 함(복호화 후 필터 필요) → MAX_SCAN_PAGES 범위에서만 계산
        int cnt = 0;
        for (int scanPage = 0; scanPage < MAX_SCAN_PAGES; scanPage++) {
            int offset = scanPage * FIXED_SIZE;
            List<UserRowRawDTO> raws = userDao.findUsers(keyword, withdrawn, FIXED_SIZE, offset);
            if (raws.isEmpty()) break;

            for (UserRowRawDTO r : raws) {
                if (matchExtraFilters(toView(r), emailFilter, phoneFilter)) cnt++;
            }
        }
        return cnt;
    }

    private boolean matchExtraFilters(UserRowViewDTO v, String emailFilter, String phoneFilter) {
        if (hasText(emailFilter)) {
            String target = emailFilter.trim().toLowerCase();
            String email = (v.email() == null) ? "" : v.email().trim().toLowerCase();
            if (!email.equals(target)) return false;
        }
        if (hasText(phoneFilter)) {
            String targetDigits = phoneFilter.replaceAll("[^0-9]", "");
            String phoneDigits = (v.phone() == null) ? "" : v.phone().replaceAll("[^0-9]", "");
            if (!phoneDigits.equals(targetDigits)) return false;
        }
        return true;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private UserRowViewDTO toView(UserRowRawDTO r) {
        try {
            String emailPlain = (r.emailEnc() == null) ? null : protector.plainEmail(EncryptedString.of(r.emailEnc()));
            String phonePlain = (r.phoneEnc() == null) ? null : protector.plainPhone(EncryptedString.of(r.phoneEnc()));

            return new UserRowViewDTO(
                    r.usersId(),
                    r.name(),
                    emailPlain,
                    phonePlain,
                    r.joinedAt(),
                    r.withdrawn()
            );
        } catch (Exception e) {
            return new UserRowViewDTO(
                    r.usersId(),
                    r.name(),
                    "(decrypt-fail)",
                    "(decrypt-fail)",
                    r.joinedAt(),
                    r.withdrawn()
            );
        }
    }
}
