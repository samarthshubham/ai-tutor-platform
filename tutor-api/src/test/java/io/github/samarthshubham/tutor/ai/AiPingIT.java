package io.github.samarthshubham.tutor.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.ai.api.AiFeature;
import io.github.samarthshubham.tutor.ai.fake.FakeChatModel;
import io.github.samarthshubham.tutor.ai.log.AiCallLog;
import io.github.samarthshubham.tutor.ai.log.AiCallLogRepository;
import io.github.samarthshubham.tutor.ai.log.AiCallLogger;
import io.github.samarthshubham.tutor.ai.log.AiCallOutcome;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.IntegrationTest;
import io.github.samarthshubham.tutor.support.TestJwt;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;

class AiPingIT extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired AiCallLogRepository logs;
  @Autowired FakeChatModel fakeChatModel;
  @Autowired AiCallLogger callLogger;
  @Autowired PlatformTransactionManager transactionManager;

  @BeforeEach
  void clearLog() {
    logs.deleteAll();
  }

  @Test
  void pingRepliesPongAndWritesOneOkRow() throws Exception {
    mvc.perform(get("/api/ai/ping").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("fake"))
        .andExpect(jsonPath("$.model").value("fake"))
        .andExpect(jsonPath("$.reply").value("pong"))
        .andExpect(jsonPath("$.latencyMs").isNumber());

    List<AiCallLog> rows = logs.findAll();
    assertThat(rows).hasSize(1);
    AiCallLog row = rows.getFirst();
    assertThat(row.getFeature()).isEqualTo(AiFeature.PING);
    assertThat(row.getPromptVersion()).isEqualTo("ping.v1");
    assertThat(row.getProvider()).isEqualTo("fake");
    assertThat(row.getModel()).isEqualTo("fake");
    assertThat(row.getLatencyMs()).isNotNegative();
    assertThat(row.getOutcome()).isEqualTo(AiCallOutcome.OK);
    assertThat(row.getCompletionTokens()).isEqualTo(1);
    assertThat(row.getCreatedAt()).isNotNull();
  }

  @Test
  void modelTimeoutWritesTimeoutRowAndReturns503() throws Exception {
    fakeChatModel.failNext(
        new ResourceAccessException("I/O error", new SocketTimeoutException("Read timed out")));

    mvc.perform(get("/api/ai/ping").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));

    assertThat(logs.findAll())
        .singleElement()
        .satisfies(row -> {
          assertThat(row.getOutcome()).isEqualTo(AiCallOutcome.TIMEOUT);
          assertThat(row.getError()).contains("SocketTimeoutException");
        });
  }

  @Test
  void emptyReplyIsLoggedAsInvalidOutput() throws Exception {
    fakeChatModel.enqueue(" ");

    mvc.perform(get("/api/ai/ping").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isInternalServerError());

    assertThat(logs.findAll())
        .singleElement()
        .extracting(AiCallLog::getOutcome)
        .isEqualTo(AiCallOutcome.INVALID_OUTPUT);
  }

  @Test
  void logRowSurvivesARolledBackBusinessTransaction() {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(status -> {
          callLogger.record(AiFeature.PING, "ping.v1", null, () -> "ok");
          status.setRollbackOnly();
        });

    assertThat(logs.findAll()).hasSize(1);
  }

  @Test
  void metersAppearInPrometheusAfterAPing() throws Exception {
    mvc.perform(get("/api/ai/ping").with(TestJwt.as(Role.ADMIN))).andExpect(status().isOk());

    mvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("tutor_ai_calls_total{")))
        .andExpect(content().string(containsString("tutor_ai_latency_seconds_count{")));
  }

  @Test
  void embeddingReturns768DimensionsAndIsLogged() throws Exception {
    mvc.perform(
            post("/api/ai/embedding")
                .with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Plants turn light into sugar.\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("fake"))
        .andExpect(jsonPath("$.dimensions").value(768));

    assertThat(logs.findAll())
        .singleElement()
        .satisfies(row -> {
          assertThat(row.getFeature()).isEqualTo(AiFeature.PING);
          assertThat(row.getPromptVersion()).isNull();
          assertThat(row.getOutcome()).isEqualTo(AiCallOutcome.OK);
        });
  }
}
