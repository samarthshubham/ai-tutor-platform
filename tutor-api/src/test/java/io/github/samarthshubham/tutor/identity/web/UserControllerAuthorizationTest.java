package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.UserResponse;
import io.github.samarthshubham.tutor.identity.service.UserService;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebSliceTest(UserController.class)
class UserControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean UserService users;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.ADMIN, SCHOOL_ID));
    when(users.search(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    when(users.getInSchool(any(), any()))
        .thenReturn(new UserResponse(ID, "u", "U", null, Role.STUDENT, SCHOOL_ID));
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

  @ParameterizedTest(name = "GET /api/users as {0} -> {1}")
  @MethodSource("adminOnly")
  void listUsers(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, get("/api/users"))).andExpect(status().is(expectedStatus));
  }

  @ParameterizedTest(name = "GET /api/users/<id> as {0} -> {1}")
  @MethodSource("adminOnly")
  void getUser(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, get("/api/users/" + ID))).andExpect(status().is(expectedStatus));
  }

  private static MockHttpServletRequestBuilder as(
      String caller, MockHttpServletRequestBuilder request) {
    return "anonymous".equals(caller) ? request : request.with(TestJwt.as(Role.valueOf(caller)));
  }
}
