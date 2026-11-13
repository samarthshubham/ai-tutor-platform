package io.github.samarthshubham.tutor.ai.config;

import java.time.Duration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@Profile({"ollama", "cloud"})
public class OllamaClientConfig {

  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

  @Bean
  OllamaApi ollamaApi(OllamaConnectionDetails connectionDetails, AiProperties properties) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
    requestFactory.setReadTimeout(properties.timeout().ping());
    return OllamaApi.builder()
        .baseUrl(connectionDetails.getBaseUrl())
        .restClientBuilder(RestClient.builder().requestFactory(requestFactory))
        .build();
  }
}
