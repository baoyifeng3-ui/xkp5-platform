package com.match;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication //springboot的全局的自动配置注解
@EnableScheduling
@MapperScan({"com.match.mapper", "com.match.licensing.persistence", "com.match.agent.persistence",
        "com.match.environment.persistence"})
public class Application {

    public static void main(String[] args) {
        // 固定  的代码 启动springboot程序 初始化spring容器
        SpringApplication.run(Application.class, args);
    }

}
