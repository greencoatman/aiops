package com.repair.aiops;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.ai.tongyi.api-key=sk-a30b883429774cd9a76c7554b39f4c2a",
    "spring.cloud.ai.tongyi.api-key=sk-a30b883429774cd9a76c7554b39f4c2a"
})
class AiopsApplicationTests {

    @Test
    void contextLoads() {
    }

}
