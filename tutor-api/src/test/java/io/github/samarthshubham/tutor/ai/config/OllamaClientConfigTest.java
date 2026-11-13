package io.github.samarthshubham.tutor.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import io.github.samarthshubham.tutor.ai.api.AiFeature;
import io.github.samarthshubham.tutor.ai.log.AiCallLogRepository;
import io.github.samarthshubham.tutor.ai.log.AiCallLogger;
import io.github.samarthshubham.tutor.common.api.AiUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.Message;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.springframework.transaction.PlatformTransactionManager;

class OllamaClientConfigTest {

  private static final Duration PING_TIMEOUT = Duration.ofMillis(300);

  private final CountDownLatch release = new CountDownLatch(1);
  private HttpServer slowOllama;

  @BeforeEach
  void startSlowServer() throws IOException {
    slowOllama = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    slowOllama.createContext(
        "/",
        exchange -> {
          try {
            release.await(10, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(500, -1);
          exchange.close();
        });
    slowOllama.start();
  }

  @AfterEach
  void stopSlowServer() {
    release.countDown();
    slowOllama.stop(0);
  }

  @Test
  void pingTimeoutOnTheHttpClientEndsTheCallAsTimeout() {
    OllamaApi api =
        new OllamaClientConfig()
            .ollamaApi(() -> "http://127.0.0.1:" + slowOllama.getAddress().getPort(), properties());
    AiCallLogger logger =
        new AiCallLogger(
            mock(AiCallLogRepository.class),
            properties(),
            new SimpleMeterRegistry(),
            Clock.systemUTC(),
            mock(PlatformTransactionManager.class));
    ChatRequest request =
        ChatRequest.builder("llama3.2:3b")
            .messages(List.of(Message.builder(Role.USER).content("ping").build()))
            .build();

    long start = System.nanoTime();
    assertThatThrownBy(
            () -> logger.record(AiFeature.PING, "ping.v1", null, () -> api.chat(request)))
        .isInstanceOf(AiUnavailableException.class)
        .hasMessage("The model timed out");
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
  }

  private static AiProperties properties() {
    return new AiProperties(
        new AiProperties.ModelRef("ollama", "llama3.2:3b"),
        new AiProperties.ModelRef("ollama", "nomic-embed-text"),
        new AiProperties.Ollama("llama3.2:3b"),
        new AiProperties.Cloud("claude-haiku-5-5"),
        new AiProperties.Timeout(
            PING_TIMEOUT,
            Duration.ofSeconds(10),
            Duration.ofSeconds(120),
            Duration.ofSeconds(60),
            Duration.ofSeconds(30)));
  }
}
