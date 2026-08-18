package com.match;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication //springboot的全局的自动配置注解
@MapperScan("com.match.mapper")
public class Application {

    public static void main(String[] args) {
        // 固定  的代码 启动springboot程序 初始化spring容器
        SpringApplication.run(Application.class, args);
    }

}
