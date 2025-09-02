package com.paulyang.ecommerce.service;

import com.paulyang.ecommerce.dto.Result;
import com.paulyang.ecommerce.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * service class
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    Result queryBlogLikes(Long id);

    Result saveBlog(Blog blog);

    Result queryBlogOfFollow(Long max, Integer offset);
}
