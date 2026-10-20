package com.smartseckill;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
@EnableAspectJAutoProxy  // 启用 AOP（@RateLimit 限流切面依赖此项）
public class SmartSeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartSeckillApplication.class, args);
    }
}
