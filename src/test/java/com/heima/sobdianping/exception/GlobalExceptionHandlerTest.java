package com.heima.sobdianping.exception;

import com.heima.sobdianping.dto.LoginDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRenderBusinessStatusAndMessage() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("验证码错误或已过期"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    void shouldHideUnexpectedExceptionDetails() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("系统繁忙，请稍后重试"));
    }

    @Test
    void shouldPreserveBadRequestForMalformedJson() throws Exception {
        mockMvc.perform(post("/test/json")
                        .contentType("application/json")
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]"})
    void shouldPreserveBadRequestForEmptyNullOrArrayJson(String payload) throws Exception {
        mockMvc.perform(post("/test/json")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldPreserveMethodNotAllowedForWrongMethod() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "验证码错误或已过期");
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("database password must not leak");
        }

        @PostMapping("/test/json")
        void json(@RequestBody LoginDTO body) {
            if (body == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "请求参数格式错误");
            }
        }
    }
}
