package com.khankiddo.learning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "QWEN_API_KEY=test-dummy-key-for-context-load")
class KhanKiddoLearningApplicationTests {

    @Test
    void contextLoads() {
    }
}
