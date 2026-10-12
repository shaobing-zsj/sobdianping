package com.heima.sobdianping.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.SecureRandom;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SecureRandom secureRandom;
    private UserServiceImpl userService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        secureRandom = mock(SecureRandom.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        userService = new UserServiceImpl(stringRedisTemplate, secureRandom);
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
}
