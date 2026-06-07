package com.plagod.startup;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;


@Slf4j
@Component
public class AutoStartupCheck implements ApplicationRunner {

    private final DataSource dataSource;

    @Value("${spring.mail.username:}")
    private String mailUsername;
    @Value("${spring.mail.password:}")
    private String mailPassword;

    public AutoStartupCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }


    @Override
    public void run(ApplicationArguments args) throws Exception {
        warmUpDatabase();
        checkMailConfig();
        log.info("认证服务启动自检完毕");
    }

    private void warmUpDatabase() throws Exception {
        try(Connection conn = dataSource.getConnection()){
            boolean valid = conn.isValid(2);

            if (!valid) {
                throw new IllegalStateException("数据库连接不可用");
            }
            log.info("数据库连接池预热完成");
        }
    }

    private void checkMailConfig(){
        boolean flag = true;
        if(!StringUtils.hasText(mailUsername)){
            log.warn("MAIL_USERNAME未配置，验证码发送将不可用");
            flag = false;
        }
        if(!StringUtils.hasText(mailPassword)){
            log.warn("MAIL_PASSWORD未配置，验证码发送将不可用");
            flag = false;
        }
        if(flag){
            log.info("邮箱发送配置检查完毕");
        }

    }

}
