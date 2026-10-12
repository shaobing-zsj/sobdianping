package com.heima.sobdianping.controller;

import com.heima.sobdianping.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService)).build();
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
}
