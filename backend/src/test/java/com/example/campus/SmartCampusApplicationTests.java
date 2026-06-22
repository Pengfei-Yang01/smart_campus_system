package com.example.campus;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * 验证应用上下文能否正常加载。
 * 如果 Spring Bean、数据库连接或配置有问题，
 * 这个测试会最先失败。
 */
@ActiveProfiles("test")
@SpringBootTest
@Sql(scripts = "/test-data/reset.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SmartCampusApplicationTests {

    @Test
    void contextLoads() {
        // 只要应用启动不抛异常，测试就算通过
    }
}
