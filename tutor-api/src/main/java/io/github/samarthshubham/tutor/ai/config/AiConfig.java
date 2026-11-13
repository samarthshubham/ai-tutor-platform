package io.github.samarthshubham.tutor.ai.config;

import io.github.samarthshubham.tutor.ai.prompt.PromptCatalog;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.ResourcePatternResolver;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

  @Bean
  static AiProfileCheck aiProfileCheck(Environment environment) {
    return new AiProfileCheck(environment);
  }

  @Bean
  PromptCatalog promptCatalog(ResourcePatternResolver resolver) {
    return new PromptCatalog(resolver, PromptCatalog.DEFAULT_LOCATION);
  }
}
