package io.github.samarthshubham.tutor.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.tutor.ai.prompt.PromptCatalog.VersionedPrompt;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class PromptCatalogTest {

  private final PathMatchingResourcePatternResolver resolver =
      new PathMatchingResourcePatternResolver();

  @Test
  void pingReturnsTheShippedTemplateAndVersion() {
    VersionedPrompt ping = new PromptCatalog(resolver, PromptCatalog.DEFAULT_LOCATION).get("ping");

    assertThat(ping.version()).isEqualTo("ping.v1");
    assertThat(ping.template()).startsWith("feature: PING\n");
  }

  @Test
  void returnsTheNewestVersionByNumber() {
    VersionedPrompt greeting =
        new PromptCatalog(resolver, "classpath:prompt-catalog/valid/").get("greeting");

    assertThat(greeting.version()).isEqualTo("greeting.v10");
    assertThat(greeting.number()).isEqualTo(10);
    assertThat(greeting.template()).contains("Greeting ten.");
  }

  @Test
  void missingPromptFails() {
    PromptCatalog catalog = new PromptCatalog(resolver, "classpath:prompt-catalog/valid/");

    assertThatThrownBy(() -> catalog.get("ping"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No prompt named 'ping'");
  }

  @Test
  void fileWithoutVersionInTheNameFails() {
    assertThatThrownBy(() -> new PromptCatalog(resolver, "classpath:prompt-catalog/bad-name/"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("greeting.st is not named <name>.v<number>.st");
  }

  @Test
  void fileWithoutAKnownFeatureLineFails() {
    assertThatThrownBy(() -> new PromptCatalog(resolver, "classpath:prompt-catalog/bad-feature/"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must start with a line 'feature: <AiFeature>'");
  }
}
