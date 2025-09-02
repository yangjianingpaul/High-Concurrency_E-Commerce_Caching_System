package com.paulyang.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * user id
     */
    private Long userId;

    /**
     * discovery hotel id
     */
    private Long blogId;

    /**
     * The associated level 1 review id, if it is a level 1 review, the value is 0
     */
    private Long parentId;

    /**
     * reply comment id
     */
    private Long answerId;

    /**
     * reply content
     */
    private String content;

    /**
     * the amount of like
     */
    private Integer liked;

    /**
     * status，0：normal，1：reported，2：forbidden to view
     */
    private Boolean status;

    /**
     * created time
     */
    private LocalDateTime createTime;

    /**
     * update time
     */
    private LocalDateTime updateTime;


}
