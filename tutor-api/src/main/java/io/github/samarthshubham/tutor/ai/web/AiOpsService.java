package io.github.samarthshubham.tutor.ai.web;

import io.github.samarthshubham.tutor.ai.api.AiFeature;
import io.github.samarthshubham.tutor.ai.api.InvalidModelOutputException;
import io.github.samarthshubham.tutor.ai.api.TextEmbedder;
import io.github.samarthshubham.tutor.ai.config.AiProperties;
import io.github.samarthshubham.tutor.ai.log.AiCallLogger;
import io.github.samarthshubham.tutor.ai.prompt.PromptCatalog;
import io.github.samarthshubham.tutor.ai.prompt.PromptCatalog.VersionedPrompt;
import java.time.Clock;
import java.util.List;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiOpsService {

  static final String PING_PROMPT = "ping";

  private final ChatModel chatModel;
  private final TextEmbedder textEmbedder;
  private final AiCallLogger callLogger;
  private final AiProperties properties;
  private final Clock clock;
  private final VersionedPrompt pingPrompt;

  public AiOpsService(
      ChatModel chatModel,
      TextEmbedder textEmbedder,
      AiCallLogger callLogger,
      AiProperties properties,
      Clock clock,
      PromptCatalog promptCatalog) {
    this.chatModel = chatModel;
    this.textEmbedder = textEmbedder;
    this.callLogger = callLogger;
    this.properties = properties;
    this.clock = clock;
    this.pingPrompt = promptCatalog.get(PING_PROMPT);
  }

  public PingResponse ping() {
    long start = clock.millis();
    Prompt prompt =
        new Prompt(List.of(new SystemMessage(pingPrompt.template()), new UserMessage(PING_PROMPT)));
    ChatResponse response =
        callLogger.record(
            AiFeature.PING, pingPrompt.version(), null, () -> validated(chatModel.call(prompt)));
    return new PingResponse(
        properties.chat().provider(),
        properties.chat().model(),
        response.getResult().getOutput().getText().trim(),
        clock.millis() - start);
  }

  public EmbeddingCheckResponse embed(String text) {
    long start = clock.millis();
    float[] vector =
        callLogger.recordEmbedding(AiFeature.PING, null, () -> textEmbedder.embed(text));
    return new EmbeddingCheckResponse(
        properties.embedding().provider(),
        properties.embedding().model(),
        vector.length,
        clock.millis() - start);
  }

  private static ChatResponse validated(ChatResponse response) {
    Generation result = response.getResult();
    if (result == null || !StringUtils.hasText(result.getOutput().getText())) {
      throw new InvalidModelOutputException("The ping reply was empty");
    }
    return response;
  }
}
