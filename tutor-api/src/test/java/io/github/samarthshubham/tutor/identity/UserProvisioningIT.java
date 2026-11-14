package io.github.samarthshubham.tutor.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.support.IntegrationTest;
import io.github.samarthshubham.tutor.support.TestJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class UserProvisioningIT extends IntegrationTest {

  private static final String SCHOOL_ID = "00000000-0000-0000-0000-000000000001";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  void meReturnsTheTeacherAndKeepsOneRowHoweverOftenItIsCalled() throws Exception {
    String username = uniqueUsername("teacher");
    String token = TestJwt.bearer(username, "TEACHER", "default-roles-tutor");

    for (int i = 0; i < 3; i++) {
      mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").isString())
          .andExpect(jsonPath("$.username").value(username))
          .andExpect(jsonPath("$.displayName").value("Test " + username))
          .andExpect(jsonPath("$.email").value(username + "@tutor.local"))
          .andExpect(jsonPath("$.role").value("TEACHER"))
          .andExpect(jsonPath("$.schoolId").value(SCHOOL_ID));
    }

    assertThat(rowsFor(username)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select keycloak_id from app_user where username = ?", String.class, username))
        .isEqualTo(TestJwt.keycloakId(username));
  }

  @Test
  void twentyConcurrentFirstRequestsCreateOneRowAndAllSucceed() throws Exception {
    String username = uniqueUsername("student");
    String token = TestJwt.bearer(username, "STUDENT");
    int requests = 20;
    CountDownLatch start = new CountDownLatch(1);
    List<Callable<Integer>> calls = new ArrayList<>();
    for (int i = 0; i < requests; i++) {
      calls.add(() -> {
        start.await();
        return mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
            .andReturn()
            .getResponse()
            .getStatus();
      });
    }

    List<Integer> statuses = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(requests)) {
      List<Future<Integer>> futures = calls.stream().map(pool::submit).toList();
      start.countDown();
      for (Future<Integer> future : futures) {
        statuses.add(future.get(30, TimeUnit.SECONDS));
      }
    }

    assertThat(statuses).hasSize(requests).containsOnly(200);
    assertThat(rowsFor(username)).isEqualTo(1);
  }

  @Test
  void aRoleChangeInTheTokenIsStoredOnTheNextRequest() throws Exception {
    String username = uniqueUsername("tutor");
    mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(username, "TUTOR")))
        .andExpect(jsonPath("$.role").value("TUTOR"));

    mvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(username, "TEACHER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("TEACHER"));

    assertThat(
            jdbc.queryForObject(
                "select role from app_user where username = ?", String.class, username))
        .isEqualTo("TEACHER");
    assertThat(rowsFor(username)).isEqualTo(1);
  }

  @Test
  void aRecreatedKeycloakUserWithTheSameUsernameGets409AndTheOldRowIsKept() throws Exception {
    String username = uniqueUsername("parent");
    mvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(username, "PARENT")))
        .andExpect(status().isOk());
    jdbc.update(
        "update app_user set keycloak_id = ? where username = ?",
        UUID.randomUUID().toString(),
        username);

    mvc.perform(
            get("/api/me").header(HttpHeaders.AUTHORIZATION, TestJwt.bearer(username, "PARENT")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));

    assertThat(rowsFor(username)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select keycloak_id from app_user where username = ?", String.class, username))
        .isNotEqualTo(TestJwt.keycloakId(username));
  }

  @Test
  void anAdminTokenReachesTheSecuredPing() throws Exception {
    mvc.perform(
            get("/api/ai/ping")
                .header(
                    HttpHeaders.AUTHORIZATION, TestJwt.bearer(uniqueUsername("admin"), "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reply").value("pong"));
  }

  private int rowsFor(String username) {
    return jdbc.queryForObject(
        "select count(*) from app_user where username = ?", Integer.class, username);
  }

  private static String uniqueUsername(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }
}
