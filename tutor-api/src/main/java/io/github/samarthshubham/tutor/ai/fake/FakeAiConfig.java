package io.github.samarthshubham.tutor.ai.fake;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("fake-ai")
public class FakeAiConfig {

  @Bean
  FakeChatModel fakeChatModel() {
    return new FakeChatModel();
  }

  @Bean
  FakeEmbeddingModel fakeEmbeddingModel() {
    return new FakeEmbeddingModel();
  }
}
