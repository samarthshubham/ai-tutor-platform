package io.github.samarthshubham.tutor.ai.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("tutor.ai")
public record AiProperties(
    @Valid @NotNull ModelRef chat,
    @Valid @NotNull ModelRef embedding,
    @Valid @NotNull Ollama ollama,
    @Valid @NotNull Cloud cloud,
    @Valid @NotNull Timeout timeout) {

  public record ModelRef(@NotBlank String provider, @NotBlank String model) {}

  public record Ollama(@NotBlank String chatModel) {}

  public record Cloud(@NotBlank String chatModel) {}

  public record Timeout(
      @NotNull Duration ping,
      @NotNull Duration search,
      @NotNull Duration generation,
      @NotNull Duration grading,
      @NotNull Duration chatFirstToken) {}
}
