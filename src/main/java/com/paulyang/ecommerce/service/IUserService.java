package com.paulyang.ecommerce.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.paulyang.ecommerce.dto.LoginFormDTO;
import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.entity.User;

import javax.servlet.http.HttpSession;

/**
 * service class
 */
public interface IUserService extends IService<User> {
    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
