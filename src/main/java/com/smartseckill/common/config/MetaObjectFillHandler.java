package com.smartseckill.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 * <p>
 * 在 insert/update 时自动设置 {@code createTime} 和 {@code updateTime}。
 * 使用 hasGetter 安全检查避免 SQL Server DATETIME2 列无默认值的 insert 失败。
 * </p>
 */
@Slf4j
@Component
public class MetaObjectFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // ⚠ SQL Server 的 DATETIME2 列设有 DEFAULT GETDATE()
        // 仅在实体没有显式传值时才填充，避免覆盖用户传入的时间
        Object createTime = this.getFieldValByName("createTime", metaObject);
        if (createTime == null) {
            this.setFieldValByName("createTime", LocalDateTime.now(), metaObject);
        }
        Object updateTime = this.getFieldValByName("updateTime", metaObject);
        if (updateTime == null) {
            this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.hasGetter("updateTime")) {
            this.setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
        }
    }
}
