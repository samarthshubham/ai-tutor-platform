package io.github.samarthshubham.tutor.ai.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.AuthorizationMatrix;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(AiOpsController.class)
class AiOpsControllerAuthorizationTest {

  @Autowired MockMvc mvc;

  @MockitoBean AiOpsService service;

  @BeforeEach
  void stubService() {
    when(service.ping()).thenReturn(new PingResponse("fake", "fake", "pong", 1));
    when(service.embed(anyString())).thenReturn(new EmbeddingCheckResponse("fake", "fake", 768, 1));
  }

  @Test
  void ping() {
    new AuthorizationMatrix(mvc, () -> get("/api/ai/ping"))
        .expect(200, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void embedding() {
    new AuthorizationMatrix(
            mvc,
            () ->
                post("/api/ai/embedding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"text\":\"some text\"}"))
        .expect(200, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }
}
