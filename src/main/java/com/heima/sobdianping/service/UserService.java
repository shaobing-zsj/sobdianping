package com.heima.sobdianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.sobdianping.dto.LoginDTO;
import com.heima.sobdianping.entity.User;
import com.heima.sobdianping.vo.LoginVO;

public interface UserService extends IService<User> {

    boolean sendVerificationCode(String phone);

    LoginVO login(LoginDTO loginDTO);
}
