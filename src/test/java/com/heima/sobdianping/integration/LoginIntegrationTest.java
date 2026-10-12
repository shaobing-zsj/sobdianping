package com.heima.sobdianping.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.entity.User;
import com.heima.sobdianping.exception.BusinessException;
import com.heima.sobdianping.mapper.UserMapper;
import com.heima.sobdianping.service.UserService;
import com.heima.sobdianping.vo.LoginVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@SpringBootTest
@Transactional
class LoginIntegrationTest {

    private static final String PHONE = "19900000001";
    private static final String CODE_KEY = "login:code:+86" + PHONE;

    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldCreateReuseAndRejectPhoneLoginCodesAgainstRealServices() {
        String firstTokenKey = null;
        String existingUserTokenKey = null;
        try {
            userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE));
            stringRedisTemplate.opsForValue().set(CODE_KEY, "012345", Duration.ofSeconds(60));

            LoginVO firstResult = userService.login(login(PHONE, "012345"));
            firstTokenKey = "login:token:" + firstResult.getToken();
            User user = userMapper.selectOne(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE)
            );
            assertNotNull(user);
            assertEquals(0, user.getGender());
            assertNotNull(user.getCreateTime());
            assertNotNull(user.getUpdateTime());

            Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(firstTokenKey);
            assertEquals(user.getId().toString(), session.get("id"));
            assertEquals(PHONE, session.get("phone"));
            assertEquals(2, session.size());
            Long ttl = stringRedisTemplate.getExpire(firstTokenKey);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= Duration.ofMinutes(30).toSeconds());
            assertNull(stringRedisTemplate.opsForValue().get(CODE_KEY));

            BusinessException reusedCode = assertThrows(
                    BusinessException.class,
                    () -> userService.login(login(PHONE, "012345"))
            );
            assertEquals(UNAUTHORIZED, reusedCode.getStatus());
            assertEquals(1L, userMapper.selectCount(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE)
            ));

            stringRedisTemplate.opsForValue().set(CODE_KEY, "654321", Duration.ofSeconds(60));
            LoginVO existingUserResult = userService.login(login(PHONE, "654321"));
            existingUserTokenKey = "login:token:" + existingUserResult.getToken();
            User existingUser = userMapper.selectOne(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE)
            );
            assertNotNull(existingUser);
            assertEquals(user.getId(), existingUser.getId());
            assertEquals(1L, userMapper.selectCount(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE)
            ));
            assertNull(stringRedisTemplate.opsForValue().get(CODE_KEY));

            stringRedisTemplate.opsForValue().set(CODE_KEY, "777777", Duration.ofSeconds(60));
            BusinessException wrongCode = assertThrows(
                    BusinessException.class,
                    () -> userService.login(login(PHONE, "000000"))
            );
            assertEquals(UNAUTHORIZED, wrongCode.getStatus());
            assertEquals("777777", stringRedisTemplate.opsForValue().get(CODE_KEY));

            stringRedisTemplate.opsForValue().set(CODE_KEY, "888888");
            assertTrue(stringRedisTemplate.expire(CODE_KEY, Duration.ZERO));
            assertNull(stringRedisTemplate.opsForValue().get(CODE_KEY));
            BusinessException expiredCode = assertThrows(
                    BusinessException.class,
                    () -> userService.login(login(PHONE, "888888"))
            );
            assertEquals(UNAUTHORIZED, expiredCode.getStatus());
        } finally {
            if (firstTokenKey != null) {
                stringRedisTemplate.delete(firstTokenKey);
            }
            if (existingUserTokenKey != null) {
                stringRedisTemplate.delete(existingUserTokenKey);
            }
            stringRedisTemplate.delete(CODE_KEY);
            userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE));
        }
    }

    private LoginDTO login(String phone, String code) {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setPhone(phone);
        loginDTO.setCode(code);
        return loginDTO;
    }
}
