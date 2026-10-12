package com.heima.sobdianping.integration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.entity.User;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldCreateUserAndPersistThirtyMinuteRedisSession() {
        userMapper.delete(Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE));
        stringRedisTemplate.opsForValue().set(CODE_KEY, "012345", Duration.ofSeconds(60));
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setPhone(PHONE);
        loginDTO.setCode("012345");

        LoginVO result = userService.login(loginDTO);
        String tokenKey = "login:token:" + result.getToken();

        try {
            User user = userMapper.selectOne(
                    Wrappers.<User>lambdaQuery().eq(User::getPhone, PHONE)
            );
            assertNotNull(user);
            assertEquals(0, user.getGender());
            assertNotNull(user.getCreateTime());
            assertNotNull(user.getUpdateTime());

            Map<Object, Object> session = stringRedisTemplate.opsForHash().entries(tokenKey);
            assertEquals(user.getId().toString(), session.get("id"));
            assertEquals(PHONE, session.get("phone"));
            assertEquals(2, session.size());
            Long ttl = stringRedisTemplate.getExpire(tokenKey);
            assertNotNull(ttl);
            assertTrue(ttl > 0 && ttl <= Duration.ofMinutes(30).toSeconds());
            assertNull(stringRedisTemplate.opsForValue().get(CODE_KEY));
        } finally {
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.delete(CODE_KEY);
        }
    }
}
