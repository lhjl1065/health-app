package com.bitfox.health.health;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class HealthApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthApplication.class, args);
    }

    @Bean
    public CommandLineRunner printAccessUrl() {
        return args -> {
            System.out.println("\n========================================");
            System.out.println("🎉 健康记录系统启动成功！");
            System.out.println("📱 访问地址: http://localhost:8080");
            System.out.println("========================================\n");
        };
    }

}
