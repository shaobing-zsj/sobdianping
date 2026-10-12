package com.heima.sobdianping.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.entity.User;
import com.heima.sobdianping.exception.BusinessException;
import com.heima.sobdianping.mapper.UserMapper;
import com.heima.sobdianping.service.UserService;
import com.heima.sobdianping.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final Pattern CHINA_PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final String VERIFICATION_CODE_KEY_PREFIX = "login:code:+86";
    private static final Duration VERIFICATION_CODE_TTL = Duration.ofSeconds(60);
    private static final int VERIFICATION_CODE_BOUND = 1_000_000;
    private static final String TOKEN_KEY_PREFIX = "login:token:";
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String USERNAME_PREFIX = "user_";
    private static final String RANDOM_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int RANDOM_USERNAME_LENGTH = 10;
    private static final DefaultRedisScript<Long> CONSUME_CODE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] "
                            + "then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );
    private static final DefaultRedisScript<Long> SAVE_SESSION_SCRIPT =
            new DefaultRedisScript<>(
                    "redis.call('hset', KEYS[1], 'id', ARGV[1]); "
                            + "redis.call('hset', KEYS[1], 'phone', ARGV[2]); "
                            + "return redis.call('expire', KEYS[1], ARGV[3])",
                    Long.class
            );

    private final StringRedisTemplate stringRedisTemplate;
    private final SecureRandom secureRandom;
    private final UserMapper userMapper;
    private final Supplier<UUID> uuidSupplier;

    @Autowired
    public UserServiceImpl(StringRedisTemplate stringRedisTemplate, UserMapper userMapper) {
        this(stringRedisTemplate, userMapper, new SecureRandom(), UUID::randomUUID);
    }

    UserServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            UserMapper userMapper,
            SecureRandom secureRandom,
            Supplier<UUID> uuidSupplier) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userMapper = userMapper;
        this.secureRandom = secureRandom;
        this.uuidSupplier = uuidSupplier;
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

    @Override
    public LoginVO login(LoginDTO loginDTO) {
        String phone = loginDTO == null ? null : loginDTO.getPhone();
        if (phone == null || !CHINA_PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式错误");
        }

        String requestCode = loginDTO.getCode();
        String codeKey = VERIFICATION_CODE_KEY_PREFIX + phone;
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (requestCode == null || !requestCode.equals(cachedCode)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }

        User user = findUserByPhone(phone);
        if (user == null) {
            user = createUser(phone);
        }

        String token = uuidSupplier.get().toString();
        String tokenKey = TOKEN_KEY_PREFIX + token;
        Long sessionSaved = stringRedisTemplate.execute(
                SAVE_SESSION_SCRIPT,
                List.of(tokenKey),
                user.getId().toString(),
                user.getPhone(),
                String.valueOf(TOKEN_TTL.getSeconds())
        );
        if (!Long.valueOf(1L).equals(sessionSaved)) {
            throw new IllegalStateException("Unable to set login token expiration");
        }

        Long consumed = stringRedisTemplate.execute(
                CONSUME_CODE_SCRIPT,
                List.of(codeKey),
                requestCode
        );
        if (!Long.valueOf(1L).equals(consumed)) {
            stringRedisTemplate.delete(tokenKey);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }
        return new LoginVO(token);
    }

    private User findUserByPhone(String phone) {
        return userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getPhone, phone)
        );
    }

    private User createUser(String phone) {
        User user = new User();
        user.setUsername(USERNAME_PREFIX + randomText(RANDOM_USERNAME_LENGTH));
        user.setGender(0);
        user.setPhone(phone);
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException exception) {
            User concurrentUser = findUserByPhone(phone);
            if (concurrentUser == null) {
                throw exception;
            }
            return concurrentUser;
        }
    }

    private String randomText(int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append(RANDOM_CHARACTERS.charAt(
                    secureRandom.nextInt(RANDOM_CHARACTERS.length())
            ));
        }
        return result.toString();
    }
}
