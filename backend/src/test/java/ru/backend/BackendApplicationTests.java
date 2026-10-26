package ru.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.backend.decision.client.MlDecisionClient;

/** Smoke-test: Spring-контекст должен подниматься без ошибок */

@SpringBootTest
@Testcontainers
class BackendApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    MlDecisionClient mlClient;

    @Test
    void contextLoads() {}
}
