package com.heima.sobdianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.heima.sobdianping.entity.User;

public interface UserService extends IService<User> {

    boolean sendVerificationCode(String phone);
}
