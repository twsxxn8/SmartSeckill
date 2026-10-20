package com.smartseckill.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 全局配置。
 * <p>
 * 注册分页插件，使 {@code selectPage()} 等方法自动生成 SQL Server 分页语句。
 * </p>
 * <p>
 * {@code @MapperScan} 放在此类而非启动类：@WebMvcTest 等切片测试会过滤掉普通
 * {@code @Configuration}，避免切片上下文注册 Mapper Bean 导致缺少 sqlSessionFactory 报错。
 * </p>
 */
@Configuration
@MapperScan("com.smartseckill.**.mapper")
public class MybatisPlusConfig {

    /** 注册分页拦截器，适配 SQL Server 方言，单页最大 500 条 */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.SQL_SERVER);
        paginationInterceptor.setMaxLimit(500L);
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}
