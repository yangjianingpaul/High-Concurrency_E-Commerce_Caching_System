package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("tb_blog")
public class Blog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /**
     * shop id
     */
    private Long shopId;
    /**
     * user id
     */
    private Long userId;
    /**
     * user icon
     */
    @TableField(exist = false)
    private String icon;
    /**
     * user name
     */
    @TableField(exist = false)
    private String name;
    /**
     * weather like
     */
    @TableField(exist = false)
    private Boolean isLike;

    /**
     * title
     */
    private String title;

    /**
     * shop photo，max:9，different photo use "," separated
     */
    private String images;

    /**
     * shop description
     */
    private String content;

    /**
     * the amount of likes
     */
    private Integer liked;

    /**
     * the amount of comment
     */
    private Integer comments;

    /**
     * created time
     */
    private LocalDateTime createTime;

    /**
     * update time
     */
    private LocalDateTime updateTime;

}
