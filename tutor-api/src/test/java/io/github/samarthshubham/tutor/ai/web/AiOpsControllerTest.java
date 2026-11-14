package io.github.samarthshubham.tutor.ai.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.AiUnavailableException;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(AiOpsController.class)
class AiOpsControllerTest {

  @Autowired MockMvc mvc;

  @MockitoBean AiOpsService service;

  @Test
  void pingReturnsProviderModelReplyAndLatency() throws Exception {
    when(service.ping()).thenReturn(new PingResponse("ollama", "llama3.2:3b", "pong", 812));

    mvc.perform(get("/api/ai/ping").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("ollama"))
        .andExpect(jsonPath("$.model").value("llama3.2:3b"))
        .andExpect(jsonPath("$.reply").value("pong"))
        .andExpect(jsonPath("$.latencyMs").value(812));
  }

  @Test
  void pingTimeoutReturns503AiUnavailable() throws Exception {
    when(service.ping()).thenThrow(new AiUnavailableException("The model timed out"));

    mvc.perform(get("/api/ai/ping").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value(503))
        .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("The model timed out"));
  }

  @Test
  void embeddingReturnsDimensions() throws Exception {
    when(service.embed("some text"))
        .thenReturn(new EmbeddingCheckResponse("ollama", "nomic-embed-text", 768, 95));

    mvc.perform(
            post("/api/ai/embedding")
                .with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"some text\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("ollama"))
        .andExpect(jsonPath("$.model").value("nomic-embed-text"))
        .andExpect(jsonPath("$.dimensions").value(768))
        .andExpect(jsonPath("$.latencyMs").value(95));
  }

  @Test
  void blankEmbeddingTextIsRejected() throws Exception {
    mvc.perform(
            post("/api/ai/embedding")
                .with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.text").exists());
  }
}
