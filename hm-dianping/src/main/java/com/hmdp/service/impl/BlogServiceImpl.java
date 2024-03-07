package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    /**
     * query blog by pagination
     *
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // based on user query
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // get current page data
        List<Blog> records = page.getRecords();
        // query user
        records.forEach(blog ->{
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     *
     * query blog based on id
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
//        query blog
        Blog blog = getById(id);
        if (blog==null) {
            return Result.fail("note does not exist！");
        }
//        query users related to blog
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * already liked
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }

        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * like function
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
//        get current user
        Long userId = UserHolder.getUser().getId();
//        determine whether the current user has liked it
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
//        if you haven't liked it you can like it
        if (score == null) {
//            database likes+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
//                save users to the redis set collection
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
//            If you have liked it, cancel the like, and the database likes -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
//            remove the user from the redis set collection
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    /**
     * query top 5 like users
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
//        query the top 5 like users zrange key 0 4
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
//        parse out the user id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
//        query users based on user id
        List<UserDTO> userDTOS = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id," + idStr +")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    /**
     * add new note
     *
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // get logged in user
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // save the store visit blog post
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("failed to add note！");
        }
//        query all fans of the note author
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
//        push notes to fans
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        // return id
        return Result.ok(blog.getId());
    }

    /**
     * note push
     *
     * @param max
     * @param offset
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
//        get current user
        Long userId = UserHolder.getUser().getId();
//        check your inbox
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
//        non empty judgment
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
//        analytical data
        List<Long> ids = new ArrayList<>();
        long miniTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String idStr = typedTuple.getValue();
            ids.add(Long.valueOf(idStr));
            long time = typedTuple.getScore().longValue();
            if (time == miniTime) {
                os++;
            } else {
                miniTime = time;
                os = 1;
            }
        }
//        query blog based on id
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

//        encapsulate and return
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(os);
        r.setMinTime(miniTime);
        return Result.ok(r);
    }

    /**
     * query blog users
     *
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
