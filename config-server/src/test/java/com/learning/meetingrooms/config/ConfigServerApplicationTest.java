package com.learning.meetingrooms.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.cloud.config.server.git.uri=file:../config-repository"
)
class ConfigServerApplicationTest {
    @Test
    void startsWithNativeRepository() {
    }
}
