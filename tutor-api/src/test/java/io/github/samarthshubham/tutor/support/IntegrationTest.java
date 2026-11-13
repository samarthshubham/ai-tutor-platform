package io.github.samarthshubham.tutor.support;

import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics
@ActiveProfiles("test")
@Import(IntegrationTest.SharedPostgres.class)
public abstract class IntegrationTest {

  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("tutor")
          .withUsername("tutor")
          .withPassword("tutor");

  static {
    POSTGRES.start();
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class SharedPostgres {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
      return POSTGRES;
    }
  }
}
