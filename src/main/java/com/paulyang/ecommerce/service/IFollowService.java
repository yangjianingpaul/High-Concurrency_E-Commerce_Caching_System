package com.paulyang.ecommerce.service;

import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * service class
 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

    Result followCommons(Long id);
}
