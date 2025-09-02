package com.paulyang.ecommerce.service.impl;

import com.paulyang.ecommerce.entity.UserInfo;
import com.paulyang.ecommerce.mapper.UserInfoMapper;
import com.paulyang.ecommerce.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  serviceImplementationClass
 * </p>
 *
 * @author yang
 * @since 2021-12-24
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
