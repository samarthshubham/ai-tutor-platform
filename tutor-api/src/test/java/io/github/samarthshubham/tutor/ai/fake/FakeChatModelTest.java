package io.github.samarthshubham.tutor.ai.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

class FakeChatModelTest {

  private final FakeChatModel model = new FakeChatModel();

  private static Prompt prompt(String system) {
    return new Prompt(List.of(new SystemMessage(system), new UserMessage("ping")));
  }

  @Test
  void pingPromptGetsPongWithModelAndUsage() {
    ChatResponse response = model.call(prompt("feature: PING\nReply with pong."));

    assertThat(response.getResult().getOutput().getText()).isEqualTo("pong");
    assertThat(response.getMetadata().getModel()).isEqualTo("fake");
    assertThat(response.getMetadata().getUsage().getPromptTokens()).isPositive();
    assertThat(response.getMetadata().getUsage().getCompletionTokens()).isEqualTo(1);
  }

  @Test
  void enqueuedReplyIsReturnedOnceThenCannedReplyAgain() {
    model.enqueue("custom");

    assertThat(model.call(prompt("feature: PING")).getResult().getOutput().getText())
        .isEqualTo("custom");
    assertThat(model.call(prompt("feature: PING")).getResult().getOutput().getText())
        .isEqualTo("pong");
  }

  @Test
  void failNextThrowsOnceInOrder() {
    IllegalStateException failure = new IllegalStateException("timeout");
    model.failNext(failure);
    model.enqueue("after");

    assertThatThrownBy(() -> model.call(prompt("feature: PING"))).isSameAs(failure);
    assertThat(model.call(prompt("feature: PING")).getResult().getOutput().getText())
        .isEqualTo("after");
  }

  @Test
  void promptWithoutMarkerFails() {
    assertThatThrownBy(() -> model.call(new Prompt(new UserMessage("hello"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("no 'feature: <AiFeature>' marker");
  }

  @Test
  void featureWithoutCannedReplyFails() {
    assertThatThrownBy(() -> model.call(prompt("feature: GRADING")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("GRADING");
  }

  @Test
  void emptyReplyCountsZeroCompletionTokens() {
    model.enqueue(" ");

    assertThat(model.call(prompt("feature: PING")).getMetadata().getUsage().getCompletionTokens())
        .isZero();
  }
}
