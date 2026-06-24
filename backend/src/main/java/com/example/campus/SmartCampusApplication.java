package com.example.campus;

import com.example.campus.ai.AiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 后端应用程序入口。
 *
 * 项目根包位于所有功能包的上层，这样后端框架可以自动扫描控制器、
 * 配置类、安全组件、数据传输对象和公共工具类。
 */
@SpringBootApplication
@EnableConfigurationProperties(AiProperties.class)
public class SmartCampusApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartCampusApplication.class, args);
    }
}
