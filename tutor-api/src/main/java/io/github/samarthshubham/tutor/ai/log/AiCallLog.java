package io.github.samarthshubham.tutor.ai.log;

import io.github.samarthshubham.tutor.ai.api.AiFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_call_log")
public class AiCallLog {

  @Id private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AiFeature feature;

  @Column(length = 64)
  private String promptVersion;

  @Column(nullable = false, length = 32)
  private String provider;

  @Column(nullable = false, length = 64)
  private String model;

  private UUID userId;

  private Integer promptTokens;

  private Integer completionTokens;

  @Column(nullable = false)
  private int latencyMs;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AiCallOutcome outcome;

  @Column(length = 500)
  private String error;

  @Column(nullable = false)
  private Instant createdAt;

  protected AiCallLog() {}

  AiCallLog(
      AiFeature feature,
      String promptVersion,
      String provider,
      String model,
      UUID userId,
      Integer promptTokens,
      Integer completionTokens,
      int latencyMs,
      AiCallOutcome outcome,
      String error,
      Instant createdAt) {
    this.id = UUID.randomUUID();
    this.feature = feature;
    this.promptVersion = promptVersion;
    this.provider = provider;
    this.model = model;
    this.userId = userId;
    this.promptTokens = promptTokens;
    this.completionTokens = completionTokens;
    this.latencyMs = latencyMs;
    this.outcome = outcome;
    this.error = error;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public AiFeature getFeature() {
    return feature;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getProvider() {
    return provider;
  }

  public String getModel() {
    return model;
  }

  public UUID getUserId() {
    return userId;
  }

  public Integer getPromptTokens() {
    return promptTokens;
  }

  public Integer getCompletionTokens() {
    return completionTokens;
  }

  public int getLatencyMs() {
    return latencyMs;
  }

  public AiCallOutcome getOutcome() {
    return outcome;
  }

  public String getError() {
    return error;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
