package com.smartseckill.common.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局日期配置。
 * <p>
 * 通过向 Spring Boot 注册 JavaTimeModule Bean 来覆盖默认的 LocalDateTime 序列化/反序列化。
 * Spring Boot 自动配置会拾取此 Module 并注册到所有 ObjectMapper 实例。
 * </p>
 * <p>
 * 兼容格式：
 * <ul>
 *   <li>{@code yyyy-MM-dd HH:mm:ss}</li>
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss}</li>
 *   <li>{@code yyyy-MM-dd'T'HH:mm:ss.SSS}</li>
 * </ul>
 * </p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Module javaTimeModule() {
        JavaTimeModule module = new JavaTimeModule();

        // 序列化：统一输出 yyyy-MM-dd HH:mm:ss
        module.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 反序列化：使用灵活解析器兼容多种格式
        module.addDeserializer(LocalDateTime.class,
                new FlexibleLocalDateTimeDeserializer());

        return module;
    }
}
