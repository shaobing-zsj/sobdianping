package com.heima.sobdianping.service.impl;

import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.entity.User;
import com.heima.sobdianping.exception.BusinessException;
import com.heima.sobdianping.mapper.UserMapper;
import com.heima.sobdianping.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class UserServiceImplTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UserMapper userMapper;
    private HashOperations<String, Object, Object> hashOperations;
    private SecureRandom secureRandom;
    private Supplier<UUID> uuidSupplier;
    private UserServiceImpl userService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        hashOperations = mock(HashOperations.class);
        secureRandom = mock(SecureRandom.class);
        userMapper = mock(UserMapper.class);
        uuidSupplier = mock(Supplier.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        userService = new UserServiceImpl(stringRedisTemplate, userMapper, secureRandom, uuidSupplier);
    }

    @Test
    void shouldStoreSixDigitCodeWithCountryCodeAndSixtySecondTtl() {
        when(secureRandom.nextInt(1_000_000)).thenReturn(42);

        boolean result = userService.sendVerificationCode("13800138000");

        assertTrue(result);
        verify(valueOperations).set(
                "login:code:+8613800138000",
                "000042",
                Duration.ofSeconds(60)
        );
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "1380013800", "138001380000", "+8613800138000", "12800138000", "1380013800a"})
    void shouldRejectInvalidPhoneWithoutWritingRedis(String phone) {
        boolean result = userService.sendVerificationCode(phone);

        assertFalse(result);
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void shouldRejectInvalidLoginPhone() {
        LoginDTO loginDTO = login("12800138000", "123456");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.login(loginDTO)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("手机号格式错误", exception.getMessage());
        verify(stringRedisTemplate, never()).opsForValue();
    }

    @Test
    void shouldRejectMissingOrDifferentVerificationCodeWithoutDeletingIt() {
        LoginDTO loginDTO = login("13800138000", "123456");
        when(valueOperations.get("login:code:+8613800138000")).thenReturn("654321");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> userService.login(loginDTO)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(stringRedisTemplate, never()).delete("login:code:+8613800138000");
        verifyNoInteractions(userMapper);
    }

    @Test
    void shouldLoginExistingUserAndStoreMinimalSession() {
        LoginDTO loginDTO = login("13800138000", "123456");
        User user = new User();
        user.setId(1001L);
        user.setPhone("13800138000");
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        when(valueOperations.get("login:code:+8613800138000")).thenReturn("123456");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(uuidSupplier.get()).thenReturn(uuid);
        when(stringRedisTemplate.execute(
                any(),
                eq(java.util.List.of("login:token:" + uuid)),
                eq("1001"),
                eq("13800138000"),
                eq("1800")
        )).thenReturn(1L);
        when(stringRedisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);

        LoginVO result = userService.login(loginDTO);

        assertEquals(uuid.toString(), result.getToken());
        verify(stringRedisTemplate).execute(
                any(),
                eq(java.util.List.of("login:token:" + uuid)),
                eq("1001"),
                eq("13800138000"),
                eq("1800")
        );
        verify(hashOperations, never()).putAll(anyString(), anyMap());
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void shouldCreateNewUserWithDefaultsBeforeLogin() {
        LoginDTO loginDTO = login("13900139000", "123456");
        UUID uuid = UUID.fromString("f47ac10b-58cc-4372-a567-0e02b2c3d479");
        when(valueOperations.get("login:code:+8613900139000")).thenReturn("123456");
        when(userMapper.selectOne(any())).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User inserted = invocation.getArgument(0);
            inserted.setId(2002L);
            return 1;
        });
        when(uuidSupplier.get()).thenReturn(uuid);
        when(stringRedisTemplate.execute(
                any(), anyList(), eq("2002"), eq("13900139000"), eq("1800")
        )).thenReturn(1L);
        when(stringRedisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);

        userService.login(loginDTO);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertEquals("13900139000", captor.getValue().getPhone());
        assertEquals(0, captor.getValue().getGender());
        assertTrue(captor.getValue().getUsername().matches("user_[A-Za-z0-9]{10}"));
    }

    @Test
    void shouldKeepVerificationCodeWhenSessionWriteFails() {
        LoginDTO loginDTO = login("13800138000", "123456");
        User user = new User();
        user.setId(1001L);
        user.setPhone("13800138000");
        when(valueOperations.get("login:code:+8613800138000")).thenReturn("123456");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(uuidSupplier.get()).thenReturn(UUID.randomUUID());
        when(stringRedisTemplate.execute(
                any(), anyList(), eq("1001"), eq("13800138000"), eq("1800")
        )).thenThrow(new IllegalStateException("write failed"));

        assertThrows(IllegalStateException.class, () -> userService.login(loginDTO));

        verify(stringRedisTemplate, never()).execute(any(), anyList(), anyString());
    }

    @Test
    void shouldReuseConcurrentUserWhenUniquePhoneInsertLosesRace() {
        LoginDTO loginDTO = login("13700137000", "123456");
        User concurrentUser = new User();
        concurrentUser.setId(3003L);
        concurrentUser.setPhone("13700137000");
        UUID uuid = UUID.fromString("9c858901-8a57-4791-81fe-4c455b099bc9");
        when(valueOperations.get("login:code:+8613700137000")).thenReturn("123456");
        when(userMapper.selectOne(any())).thenReturn(null, concurrentUser);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("uk_phone"));
        when(uuidSupplier.get()).thenReturn(uuid);
        when(stringRedisTemplate.execute(
                any(), eq(java.util.List.of("login:token:" + uuid)),
                eq("3003"), eq("13700137000"), eq("1800")
        )).thenReturn(1L);
        when(stringRedisTemplate.execute(any(), anyList(), eq("123456"))).thenReturn(1L);

        LoginVO result = userService.login(loginDTO);

        assertEquals(uuid.toString(), result.getToken());
        verify(stringRedisTemplate).execute(
                any(), eq(java.util.List.of("login:token:" + uuid)),
                eq("3003"), eq("13700137000"), eq("1800")
        );
        verify(userMapper, times(2)).selectOne(any());
    }

    private LoginDTO login(String phone, String code) {
        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setPhone(phone);
        loginDTO.setCode(code);
        return loginDTO;
    }
}
