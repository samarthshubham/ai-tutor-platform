package io.github.samarthshubham.tutor.ai.log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.samarthshubham.tutor.ai.api.AiFeature;
import io.github.samarthshubham.tutor.ai.api.InvalidModelOutputException;
import io.github.samarthshubham.tutor.ai.config.AiProperties;
import io.github.samarthshubham.tutor.ai.config.AiProperties.ModelRef;
import io.github.samarthshubham.tutor.common.api.AiUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.web.client.ResourceAccessException;

class AiCallLoggerTest {

  private static final Instant NOW = Instant.parse("2026-11-13T10:00:00Z");
  private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000042");

  private final AiCallLogRepository repository = mock(AiCallLogRepository.class);
  private final PlatformTransactionManager transactionManager =
      mock(PlatformTransactionManager.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final Clock clock = mock(Clock.class);
  private final AiProperties properties =
      new AiProperties(
          new ModelRef("ollama", "llama3.2:3b"),
          new ModelRef("ollama", "nomic-embed-text"),
          new AiProperties.Ollama("llama3.2:3b"),
          new AiProperties.Cloud("claude-haiku-5-5"),
          new AiProperties.Timeout(
              Duration.ofSeconds(20),
              Duration.ofSeconds(10),
              Duration.ofSeconds(120),
              Duration.ofSeconds(60),
              Duration.ofSeconds(30)));

  private AiCallLogger logger;

  @BeforeEach
  void setUp() {
    when(clock.millis()).thenReturn(1_000L, 1_250L);
    when(clock.instant()).thenReturn(NOW);
    logger = new AiCallLogger(repository, properties, meters, clock, transactionManager);
  }

  @Test
  void successfulChatCallWritesOkRowWithUsageAndMeters() {
    ChatResponse response =
        new ChatResponse(
            List.of(new Generation(new AssistantMessage("pong"))),
            ChatResponseMetadata.builder().usage(new DefaultUsage(12, 3)).build());

    ChatResponse result = logger.record(AiFeature.PING, "ping.v1", USER, () -> response);

    assertThat(result).isSameAs(response);
    AiCallLog row = savedRow();
    assertThat(row.getId()).isNotNull();
    assertThat(row.getFeature()).isEqualTo(AiFeature.PING);
    assertThat(row.getPromptVersion()).isEqualTo("ping.v1");
    assertThat(row.getProvider()).isEqualTo("ollama");
    assertThat(row.getModel()).isEqualTo("llama3.2:3b");
    assertThat(row.getUserId()).isEqualTo(USER);
    assertThat(row.getPromptTokens()).isEqualTo(12);
    assertThat(row.getCompletionTokens()).isEqualTo(3);
    assertThat(row.getLatencyMs()).isEqualTo(250);
    assertThat(row.getOutcome()).isEqualTo(AiCallOutcome.OK);
    assertThat(row.getError()).isNull();
    assertThat(row.getCreatedAt()).isEqualTo(NOW);
    assertThat(
            meters
                .get("tutor.ai.calls")
                .tags("feature", "PING", "outcome", "OK", "provider", "ollama")
                .counter()
                .count())
        .isEqualTo(1);
    assertThat(meters.get("tutor.ai.latency").tags("feature", "PING").timer().count()).isEqualTo(1);
    assertThat(meters.get("tutor.ai.tokens").tags("kind", "prompt").counter().count())
        .isEqualTo(12);
    assertThat(meters.get("tutor.ai.tokens").tags("kind", "completion").counter().count())
        .isEqualTo(3);
  }

  @Test
  void writesInANewTransaction() {
    logger.record(AiFeature.PING, "ping.v1", null, () -> "ok");

    ArgumentCaptor<TransactionDefinition> definition =
        ArgumentCaptor.forClass(TransactionDefinition.class);
    verify(transactionManager).getTransaction(definition.capture());
    assertThat(definition.getValue().getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Test
  void embeddingCallUsesEmbeddingModelAndHasNoTokens() {
    float[] vector = logger.recordEmbedding(AiFeature.PING, null, () -> new float[768]);

    assertThat(vector).hasSize(768);
    AiCallLog row = savedRow();
    assertThat(row.getModel()).isEqualTo("nomic-embed-text");
    assertThat(row.getPromptVersion()).isNull();
    assertThat(row.getPromptTokens()).isNull();
    assertThat(meters.find("tutor.ai.tokens").counter()).isNull();
  }

  @Test
  void timeoutWritesTimeoutRowAndThrowsAiUnavailable() {
    ResourceAccessException timeout =
        new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out"));

    assertThatThrownBy(() ->
            logger.record(
                AiFeature.PING,
                "ping.v1",
                null,
                () -> {
                  throw timeout;
                }))
        .isInstanceOf(AiUnavailableException.class)
        .hasMessage("The model timed out")
        .hasCause(timeout);

    AiCallLog row = savedRow();
    assertThat(row.getOutcome()).isEqualTo(AiCallOutcome.TIMEOUT);
    assertThat(row.getError()).isEqualTo("java.net.SocketTimeoutException: Read timed out");
    assertThat(meters.get("tutor.ai.calls").tags("outcome", "TIMEOUT").counter().count())
        .isEqualTo(1);
  }

  @Test
  void otherFailureWritesErrorRowWithTruncatedMessage() {
    IllegalStateException failure = new IllegalStateException("x".repeat(600));

    assertThatThrownBy(() ->
            logger.record(
                AiFeature.PING,
                null,
                null,
                () -> {
                  throw failure;
                }))
        .isInstanceOf(AiUnavailableException.class)
        .hasMessage("The model could not be reached");

    AiCallLog row = savedRow();
    assertThat(row.getOutcome()).isEqualTo(AiCallOutcome.ERROR);
    assertThat(row.getError()).hasSize(AiCallLogger.MAX_ERROR_LENGTH);
  }

  @Test
  void failureWithoutMessageStoresTheExceptionClass() {
    assertThatThrownBy(() ->
            logger.record(
                AiFeature.PING,
                null,
                null,
                () -> {
                  throw new IllegalStateException();
                }))
        .isInstanceOf(AiUnavailableException.class);

    assertThat(savedRow().getError()).isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  void invalidOutputWritesInvalidOutputRowAndRethrows() {
    InvalidModelOutputException invalid = new InvalidModelOutputException("empty");

    assertThatThrownBy(() ->
            logger.record(
                AiFeature.PING,
                "ping.v1",
                null,
                () -> {
                  throw invalid;
                }))
        .isSameAs(invalid);

    assertThat(savedRow().getOutcome()).isEqualTo(AiCallOutcome.INVALID_OUTPUT);
  }

  private AiCallLog savedRow() {
    ArgumentCaptor<AiCallLog> row = ArgumentCaptor.forClass(AiCallLog.class);
    verify(repository).save(row.capture());
    return row.getValue();
  }
}
