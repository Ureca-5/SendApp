package com.mycom.myapp.sendapp.admin.user.service;

import com.mycom.myapp.sendapp.global.crypto.ContactProtector;
import com.mycom.myapp.sendapp.global.crypto.EncryptedString;
import com.mycom.myapp.sendapp.admin.user.dao.UserDao;
import com.mycom.myapp.sendapp.admin.user.dto.UserRowRawDTO;
import com.mycom.myapp.sendapp.admin.user.dto.UserRowViewDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserDao userDao;
    private final ContactProtector protector;

    public UserService(UserDao userDao, ContactProtector protector) {
        this.userDao = userDao;
        this.protector = protector;
    }

    public int count(String keyword, Boolean withdrawn) {
        return userDao.countUsers(keyword, withdrawn);
    }

    public List<UserRowViewDTO> list(String keyword, Boolean withdrawn, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200); // 과도한 size 방지
        int offset = safePage * safeSize;

        List<UserRowRawDTO> raws = userDao.findUsers(keyword, withdrawn, safeSize, offset);

        return raws.stream().map(r -> {
			try {
				return new UserRowViewDTO(
				        r.usersId(),
				        r.name(),
				        protector.maskedEmail(EncryptedString.of(r.emailEnc())),
				        protector.maskedPhone(EncryptedString.of(r.phoneEnc())),
				        r.joinedAt(),
				        r.withdrawn()
				);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
		}).toList();
    }
}
