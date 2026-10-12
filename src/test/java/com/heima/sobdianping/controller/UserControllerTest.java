package com.heima.sobdianping.controller;

import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.exception.BusinessException;
import com.heima.sobdianping.exception.GlobalExceptionHandler;
import com.heima.sobdianping.service.UserService;
import com.heima.sobdianping.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnNoContentAndNotExposeCodeWhenPhoneIsValid() throws Exception {
        when(userService.sendVerificationCode("13800138000")).thenReturn(true);

        mockMvc.perform(post("/user/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void shouldReturnBadRequestWhenPhoneIsInvalid() throws Exception {
        when(userService.sendVerificationCode("12800138000")).thenReturn(false);

        mockMvc.perform(post("/user/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12800138000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void shouldReturnTokenWhenLoginSucceeds() throws Exception {
        when(userService.login(any(LoginDTO.class))).thenReturn(
                new LoginVO("550e8400-e29b-41d4-a716-446655440000")
        );

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\",\"code\":\"012345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.token")
                        .value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void shouldReturnBadRequestForInvalidPhone() throws Exception {
        when(userService.login(any(LoginDTO.class))).thenThrow(
                new BusinessException(HttpStatus.BAD_REQUEST, "手机号格式错误")
        );

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12800138000\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("手机号格式错误"));
    }

    @Test
    void shouldReturnUnauthorizedForInvalidCode() throws Exception {
        when(userService.login(any(LoginDTO.class))).thenThrow(
                new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期")
        );

        mockMvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\",\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("验证码错误或已过期"));
    }
}
