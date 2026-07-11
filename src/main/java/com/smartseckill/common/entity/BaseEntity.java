package com.smartseckill.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类。
 * <p>
 * 所有数据库实体（除纯关联表外）继承此类，统一提供主键、时间戳和逻辑删除字段。
 * 时间字段由 {@link MetaObjectFillHandler} 自动填充。
 * </p>
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=未删除，1=已删除（MyBatis-Plus 自动过滤） */
    @TableLogic
    private Integer deleted;
}
