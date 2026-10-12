package com.heima.sobdianping.controller;

import com.heima.sobdianping.common.Result;
import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.dto.SendCodeDTO;
import com.heima.sobdianping.service.UserService;
import com.heima.sobdianping.vo.LoginVO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String sayHello() {
        return "Hello World";
    }

    @PostMapping("/code")
    public ResponseEntity<Void> sendVerificationCode(
            @RequestBody SendCodeDTO sendCodeDTO) {
        boolean sent = userService.sendVerificationCode(sendCodeDTO.getPhone());
        if (!sent) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/login")
    public ResponseEntity<Result<LoginVO>> login(@RequestBody LoginDTO loginDTO) {
        LoginVO loginVO = userService.login(loginDTO);
        return ResponseEntity.ok(Result.success("登录成功", loginVO));
    }
}
