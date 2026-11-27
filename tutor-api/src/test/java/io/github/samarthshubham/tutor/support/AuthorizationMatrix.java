package io.github.samarthshubham.tutor.support;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import io.github.samarthshubham.tutor.identity.api.Role;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public final class AuthorizationMatrix {
  private final MockMvc mvc;
  private final Supplier<MockHttpServletRequestBuilder> request;
  private final Map<Role, Integer> statuses = new EnumMap<>(Role.class);

  public AuthorizationMatrix(MockMvc mvc, Supplier<MockHttpServletRequestBuilder> request) {
    this.mvc = mvc;
    this.request = request;
  }

  public AuthorizationMatrix expect(int status, Role... roles) {
    for (Role role : roles) {
      statuses.put(role, status);
    }
    return this;
  }

  public void verify(int anonymousStatus) {
    List<Executable> checks = new ArrayList<>();
    for (Role role : Role.values()) {
      if (!statuses.containsKey(role)) {
        throw new IllegalStateException("Missing expected status for " + role);
      }
      checks.add(
          () -> check(role.name(), request.get().with(TestJwt.as(role)), statuses.get(role)));
    }
    checks.add(() -> check("anonymous", request.get(), anonymousStatus));
    assertAll("Authorization matrix", checks);
  }

  private void check(String caller, MockHttpServletRequestBuilder row, int expectedStatus)
      throws Exception {
    var result = mvc.perform(row);
    assertEquals(expectedStatus, result.andReturn().getResponse().getStatus(), caller);
    if (expectedStatus == 401 || expectedStatus == 403) {
      String code = expectedStatus == 401 ? "UNAUTHENTICATED" : "FORBIDDEN";
      assertAll(caller, () -> result.andExpect(jsonPath("$.code").value(code)));
    }
  }
}
