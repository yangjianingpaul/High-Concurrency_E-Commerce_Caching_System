package com.paulyang.ecommerce.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_shop_type")
public class ShopType implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * primary key
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * name of type
     */
    private String name;

    /**
     * icon
     */
    private String icon;

    /**
     * order
     */
    private Integer sort;

    /**
     * create time
     */
    @JsonIgnore
    private LocalDateTime createTime;

    /**
     * update time
     */
    @JsonIgnore
    private LocalDateTime updateTime;


}
