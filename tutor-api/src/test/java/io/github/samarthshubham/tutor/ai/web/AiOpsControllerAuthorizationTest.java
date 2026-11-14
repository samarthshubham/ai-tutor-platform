package io.github.samarthshubham.tutor.ai.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebSliceTest(AiOpsController.class)
class AiOpsControllerAuthorizationTest {

  @Autowired MockMvc mvc;

  @MockitoBean AiOpsService service;

  @BeforeEach
  void stubService() {
    when(service.ping()).thenReturn(new PingResponse("fake", "fake", "pong", 1));
    when(service.embed(anyString())).thenReturn(new EmbeddingCheckResponse("fake", "fake", 768, 1));
  }

  static Stream<Arguments> adminOnly() {
    return Stream.of(
        Arguments.of("ADMIN", 200),
        Arguments.of("TEACHER", 403),
        Arguments.of("TUTOR", 403),
        Arguments.of("STUDENT", 403),
        Arguments.of("PARENT", 403),
        Arguments.of("anonymous", 401));
  }

  @ParameterizedTest(name = "GET /api/ai/ping as {0} -> {1}")
  @MethodSource("adminOnly")
  void ping(String caller, int expectedStatus) throws Exception {
    expect(mvc.perform(as(caller, get("/api/ai/ping"))), expectedStatus);
  }

  @ParameterizedTest(name = "POST /api/ai/embedding as {0} -> {1}")
  @MethodSource("adminOnly")
  void embedding(String caller, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder request =
        post("/api/ai/embedding")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"some text\"}");
    expect(mvc.perform(as(caller, request)), expectedStatus);
  }

  private static void expect(ResultActions result, int expectedStatus) throws Exception {
    result.andExpect(status().is(expectedStatus));
    if (expectedStatus == 403) {
      result.andExpect(jsonPath("$.code").value("FORBIDDEN"));
    } else if (expectedStatus == 401) {
      result.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
  }

  private static MockHttpServletRequestBuilder as(
      String caller, MockHttpServletRequestBuilder request) {
    return "anonymous".equals(caller) ? request : request.with(TestJwt.as(Role.valueOf(caller)));
  }
}
