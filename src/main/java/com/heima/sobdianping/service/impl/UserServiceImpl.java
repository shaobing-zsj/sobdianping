package com.heima.sobdianping.service.impl;

import com.heima.sobdianping.entity.User;
import com.heima.sobdianping.mapper.UserMapper;
import com.heima.sobdianping.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Pattern CHINA_PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final String VERIFICATION_CODE_KEY_PREFIX = "login:code:+86";
    private static final Duration VERIFICATION_CODE_TTL = Duration.ofSeconds(60);
    private static final int VERIFICATION_CODE_BOUND = 1_000_000;

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom;

    @Autowired
    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, new SecureRandom());
    }

    UserServiceImpl(StringRedisTemplate stringRedisTemplate, SecureRandom secureRandom) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.secureRandom = secureRandom;
    }

    @Override
    public boolean sendVerificationCode(String phone) {
        if (phone == null || !CHINA_PHONE_PATTERN.matcher(phone).matches()) {
            return false;
        }

        String verificationCode = String.format(
                Locale.ROOT,
                "%06d",
                secureRandom.nextInt(VERIFICATION_CODE_BOUND)
        );
        stringRedisTemplate.opsForValue().set(
                VERIFICATION_CODE_KEY_PREFIX + phone,
                verificationCode,
                VERIFICATION_CODE_TTL
        );
        return true;
    }
}
