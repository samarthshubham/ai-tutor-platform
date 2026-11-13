package io.github.samarthshubham.tutor.ai.log;

import io.github.samarthshubham.tutor.ai.api.AiFeature;
import io.github.samarthshubham.tutor.ai.api.InvalidModelOutputException;
import io.github.samarthshubham.tutor.ai.config.AiProperties;
import io.github.samarthshubham.tutor.ai.config.AiProperties.ModelRef;
import io.github.samarthshubham.tutor.common.api.AiUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AiCallLogger {

  static final int MAX_ERROR_LENGTH = 500;

  private final AiCallLogRepository repository;
  private final AiProperties properties;
  private final MeterRegistry meterRegistry;
  private final Clock clock;
  private final TransactionTemplate newTransaction;

  public AiCallLogger(
      AiCallLogRepository repository,
      AiProperties properties,
      MeterRegistry meterRegistry,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
    this.clock = clock;
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public <T> T record(AiFeature feature, String promptVersion, UUID userId, Supplier<T> modelCall) {
    return call(properties.chat(), feature, promptVersion, userId, modelCall);
  }

  public <T> T recordEmbedding(AiFeature feature, UUID userId, Supplier<T> modelCall) {
    return call(properties.embedding(), feature, null, userId, modelCall);
  }

  private <T> T call(
      ModelRef model, AiFeature feature, String promptVersion, UUID userId, Supplier<T> modelCall) {
    long start = clock.millis();
    T result;
    try {
      result = modelCall.get();
    } catch (RuntimeException e) {
      AiCallOutcome outcome = outcomeOf(e);
      write(model, feature, promptVersion, userId, null, start, outcome, describe(e));
      if (e instanceof InvalidModelOutputException) {
        throw e;
      }
      throw new AiUnavailableException(
          outcome == AiCallOutcome.TIMEOUT
              ? "The model timed out"
              : "The model could not be reached",
          e);
    }
    Usage usage =
        result instanceof ChatResponse response ? response.getMetadata().getUsage() : null;
    write(model, feature, promptVersion, userId, usage, start, AiCallOutcome.OK, null);
    return result;
  }

  private void write(
      ModelRef model,
      AiFeature feature,
      String promptVersion,
      UUID userId,
      Usage usage,
      long start,
      AiCallOutcome outcome,
      String error) {
    int latencyMs = (int) (clock.millis() - start);
    Integer promptTokens = usage == null ? null : usage.getPromptTokens();
    Integer completionTokens = usage == null ? null : usage.getCompletionTokens();
    newTransaction.executeWithoutResult(status ->
        repository.save(
            new AiCallLog(
                feature,
                promptVersion,
                model.provider(),
                model.model(),
                userId,
                promptTokens,
                completionTokens,
                latencyMs,
                outcome,
                error,
                clock.instant())));
    meterRegistry
        .counter(
            "tutor.ai.calls",
            "feature",
            feature.name(),
            "outcome",
            outcome.name(),
            "provider",
            model.provider())
        .increment();
    Timer.builder("tutor.ai.latency")
        .tag("feature", feature.name())
        .register(meterRegistry)
        .record(Duration.ofMillis(latencyMs));
    countTokens(feature, "prompt", promptTokens);
    countTokens(feature, "completion", completionTokens);
  }

  private void countTokens(AiFeature feature, String kind, Integer tokens) {
    if (tokens != null && tokens > 0) {
      meterRegistry
          .counter("tutor.ai.tokens", "feature", feature.name(), "kind", kind)
          .increment(tokens);
    }
  }

  private static AiCallOutcome outcomeOf(RuntimeException e) {
    if (e instanceof InvalidModelOutputException) {
      return AiCallOutcome.INVALID_OUTPUT;
    }
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      if (cause instanceof InterruptedIOException
          || cause instanceof HttpTimeoutException
          || cause instanceof TimeoutException) {
        return AiCallOutcome.TIMEOUT;
      }
    }
    return AiCallOutcome.ERROR;
  }

  private static String describe(RuntimeException e) {
    Throwable root = e;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    String text =
        root.getMessage() == null
            ? root.getClass().getName()
            : root.getClass().getName() + ": " + root.getMessage();
    return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
  }
}
