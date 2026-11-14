package io.github.samarthshubham.tutor.identity;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.support.IntegrationTest;
import io.github.samarthshubham.tutor.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

class SecurityIT extends IntegrationTest {

  @Autowired MockMvc mvc;

  @ParameterizedTest(name = "{0} answers without a token")
  @ValueSource(
      strings = {
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/prometheus",
        "/v3/api-docs",
        "/v3/api-docs/swagger-config",
        "/swagger-ui/index.html"
      })
  void openPathsAnswerWithoutAToken(String path) throws Exception {
    mvc.perform(get(path)).andExpect(status().isOk());
  }

  @Test
  void everythingElseNeedsAToken() throws Exception {
    mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/ai/ping")).andExpect(status().isUnauthorized());
  }

  @Test
  void missingTokenIs401WithTheErrorBody() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Authentication required"));
  }

  @Test
  void expiredTokenIs401() throws Exception {
    mvc.perform(
            get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.expiredBearer("expired1", "TEACHER")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Invalid or expired token"));
  }

  @Test
  void wronglySignedTokenIs401() throws Exception {
    mvc.perform(
            get("/api/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    TestJwt.bearerSignedWithUnknownKey("forged1", "ADMIN")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Invalid or expired token"));
  }

  @Test
  void malformedTokenIs401() throws Exception {
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void tokenWithNoneOfTheFiveRolesIs403() throws Exception {
    mvc.perform(
            get("/api/me")
                .header(
                    HttpHeaders.AUTHORIZATION,
                    TestJwt.bearer("norole1", "default-roles-tutor", "offline_access")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Access denied"));
  }

  @Test
  void tokenWithTwoOfTheFiveRolesIs403() throws Exception {
    mvc.perform(
            get("/api/me")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer("tworoles1", "ADMIN", "PARENT")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void nonAdminCallingAnAdminEndpointIs403WithTheErrorBody() throws Exception {
    mvc.perform(
            get("/api/ai/ping")
                .header(HttpHeaders.AUTHORIZATION, TestJwt.bearer("student9", "STUDENT")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Access denied"));
  }

  @Test
  void corsAllowsTheWebApp() throws Exception {
    mvc.perform(
            options("/api/me")
                .header(HttpHeaders.ORIGIN, "http://localhost:4300")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4300"));
  }

  @Test
  void corsRejectsOtherOrigins() throws Exception {
    mvc.perform(
            options("/api/me")
                .header(HttpHeaders.ORIGIN, "http://evil.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Test
  void openApiDeclaresTheBearerScheme() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.components.securitySchemes.bearer.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
        .andExpect(jsonPath("$.security[0].bearer").exists())
        .andExpect(jsonPath("$.paths['/api/me'].get.parameters").doesNotExist())
        .andExpect(content().string(containsString("/api/me")));
  }
}
