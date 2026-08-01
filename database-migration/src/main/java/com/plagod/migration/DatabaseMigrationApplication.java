package com.plagod.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DatabaseMigrationApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DatabaseMigrationApplication.class);

        // 迁移程序不启动 HTTP 端口，只负责数据库版本升级。
        application.setWebApplicationType(WebApplicationType.NONE);

        ConfigurableApplicationContext context = application.run(args);

        // Flyway 执行完毕后立即退出，不作为常驻微服务运行。
        context.close();
    }
}