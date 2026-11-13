package io.github.samarthshubham.tutor.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class AiPropertiesTest {

  @Configuration
  @EnableConfigurationProperties(AiProperties.class)
  static class PropertiesOnly {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(PropertiesOnly.class)
          .withPropertyValues(
              "tutor.ai.chat.provider=ollama",
              "tutor.ai.chat.model=llama3.2:3b",
              "tutor.ai.embedding.provider=ollama",
              "tutor.ai.embedding.model=nomic-embed-text",
              "tutor.ai.ollama.chat-model=llama3.2:3b",
              "tutor.ai.cloud.chat-model=claude-haiku-5-5",
              "tutor.ai.timeout.ping=20s",
              "tutor.ai.timeout.search=10s",
              "tutor.ai.timeout.generation=120s",
              "tutor.ai.timeout.grading=60s",
              "tutor.ai.timeout.chat-first-token=30s");

  @Test
  void bindsAllValues() {
    runner.run(context -> {
      AiProperties properties = context.getBean(AiProperties.class);
      assertThat(properties.chat().provider()).isEqualTo("ollama");
      assertThat(properties.embedding().model()).isEqualTo("nomic-embed-text");
      assertThat(properties.ollama().chatModel()).isEqualTo("llama3.2:3b");
      assertThat(properties.cloud().chatModel()).isEqualTo("claude-haiku-5-5");
      assertThat(properties.timeout().ping()).isEqualTo(Duration.ofSeconds(20));
      assertThat(properties.timeout().chatFirstToken()).isEqualTo(Duration.ofSeconds(30));
    });
  }

  @Test
  void blankProviderFailsAtStartup() {
    runner
        .withPropertyValues("tutor.ai.chat.provider=")
        .run(context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("chat.provider"));
  }

  @Test
  void missingTimeoutFailsAtStartup() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(PropertiesOnly.class)
        .withPropertyValues("tutor.ai.chat.provider=ollama")
        .run(context -> assertThat(context).hasFailed());
  }
}
