package io.github.samarthshubham.tutor.ai.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class AiProfileCheckTest {

  private static void check(MockEnvironment environment) {
    new AiProfileCheck(environment).postProcessBeanFactory(null);
  }

  private static MockEnvironment withProfiles(String... profiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(profiles);
    return environment;
  }

  @Test
  void oneAiProfilePasses() {
    assertThatCode(() -> check(withProfiles("dev", "fake-ai"))).doesNotThrowAnyException();
    assertThatCode(() -> check(withProfiles("ollama"))).doesNotThrowAnyException();
  }

  @Test
  void noAiProfileFailsWithClearMessage() {
    assertThatThrownBy(() -> check(withProfiles("dev")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No AI profile is active")
        .hasMessageContaining("[ollama, cloud, fake-ai]");
  }

  @Test
  void twoAiProfilesFailWithClearMessage() {
    assertThatThrownBy(() -> check(withProfiles("dev", "ollama", "fake-ai")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("More than one AI profile is active: [ollama, fake-ai]");
  }

  @Test
  void cloudWithoutApiKeyNamesTheVariable() {
    assertThatThrownBy(() -> check(withProfiles("dev", "cloud")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
    MockEnvironment blankKey = withProfiles("cloud");
    blankKey.setProperty("ANTHROPIC_API_KEY", " ");
    assertThatThrownBy(() -> check(blankKey)).hasMessageContaining("ANTHROPIC_API_KEY");
  }

  @Test
  void cloudWithApiKeyPasses() {
    MockEnvironment environment = withProfiles("dev", "cloud");
    environment.setProperty("ANTHROPIC_API_KEY", "test-key");

    assertThatCode(() -> check(environment)).doesNotThrowAnyException();
  }
}
