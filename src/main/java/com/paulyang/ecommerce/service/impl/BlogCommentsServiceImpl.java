package com.paulyang.ecommerce.service.impl;

import com.paulyang.ecommerce.entity.BlogComments;
import com.paulyang.ecommerce.mapper.BlogCommentsMapper;
import com.paulyang.ecommerce.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
