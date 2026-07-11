package com.smartseckill.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI 文档配置。
 * <p>
 * 启动后访问 {@code /api/swagger-ui.html} 查看接口文档。
 * 已配置 JWT Bearer 认证，可在 Swagger UI 中填入 Token 测试需登录的接口。
 * </p>
 */
@Configuration
public class SwaggerConfig {

    /** 配置 API 文档基本信息及 JWT 安全认证方案 */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartSeckill API")
                        .description("AI-Powered High-Concurrency Seckill Mall Backend API")
                        .version("1.0.0")
                        .contact(new Contact().name("SmartSeckill Team")))
                .addSecurityItem(new SecurityRequirement().addList("Bearer"))
                .components(new Components()
                        .addSecuritySchemes("Bearer",
                                new SecurityScheme()
                                        .name("Authorization")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
