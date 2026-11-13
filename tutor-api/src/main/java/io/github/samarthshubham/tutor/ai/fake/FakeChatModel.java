package io.github.samarthshubham.tutor.ai.fake;

import io.github.samarthshubham.tutor.ai.api.AiFeature;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

public class FakeChatModel implements ChatModel {

  public static final String MODEL = "fake";

  private static final String FEATURE_MARKER = "feature: ";

  private final Queue<Supplier<String>> nextReplies = new ConcurrentLinkedQueue<>();

  public void enqueue(String reply) {
    nextReplies.add(() -> reply);
  }

  public void failNext(RuntimeException failure) {
    nextReplies.add(() -> {
      throw failure;
    });
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    Supplier<String> queued = nextReplies.poll();
    String reply = queued != null ? queued.get() : cannedReply(featureOf(prompt));
    ChatResponseMetadata metadata =
        ChatResponseMetadata.builder()
            .model(MODEL)
            .usage(new DefaultUsage(wordCount(prompt.getContents()), wordCount(reply)))
            .build();
    return new ChatResponse(List.of(new Generation(new AssistantMessage(reply))), metadata);
  }

  private static String cannedReply(AiFeature feature) {
    if (feature == AiFeature.PING) {
      return "pong";
    }
    throw new IllegalStateException("FakeChatModel has no canned reply for feature " + feature);
  }

  private static AiFeature featureOf(Prompt prompt) {
    return prompt.getSystemMessages().stream()
        .map(SystemMessage::getText)
        .flatMap(text -> Optional.ofNullable(text).stream())
        .flatMap(String::lines)
        .filter(line -> line.startsWith(FEATURE_MARKER))
        .map(line -> AiFeature.valueOf(line.substring(FEATURE_MARKER.length()).trim()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("Prompt has no 'feature: <AiFeature>' marker line"));
  }

  private static int wordCount(String text) {
    String trimmed = text == null ? "" : text.trim();
    return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
  }
}
