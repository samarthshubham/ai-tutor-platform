package io.github.samarthshubham.tutor.ai.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

public class AiProfileCheck implements BeanFactoryPostProcessor {

  static final List<String> AI_PROFILES = List.of("ollama", "cloud", "fake-ai");
  static final String API_KEY_VARIABLE = "ANTHROPIC_API_KEY";

  private final Environment environment;

  public AiProfileCheck(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    List<String> active =
        Arrays.stream(environment.getActiveProfiles()).filter(AI_PROFILES::contains).toList();
    if (active.isEmpty()) {
      throw new IllegalStateException(
          "No AI profile is active. Activate exactly one of "
              + AI_PROFILES
              + ", for example --spring.profiles.active=dev,fake-ai");
    }
    if (active.size() > 1) {
      throw new IllegalStateException(
          "More than one AI profile is active: "
              + active
              + ". Activate exactly one of "
              + AI_PROFILES);
    }
    if (active.getFirst().equals("cloud")
        && !StringUtils.hasText(environment.getProperty(API_KEY_VARIABLE))) {
      throw new IllegalStateException(
          "The cloud AI profile needs the "
              + API_KEY_VARIABLE
              + " environment variable, which is not set");
    }
  }
}
