package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
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

@WebSliceTest(MeController.class)
class MeControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean UserService users;
  @MockitoBean RelationshipService relationships;

  @BeforeEach
  void stubUser() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.STUDENT, SCHOOL_ID));
    when(users.get(any()))
        .thenReturn(new UserResponse(ID, "u", "U", "u@tutor.local", Role.STUDENT, SCHOOL_ID));
    when(relationships.studentsOf(any())).thenReturn(List.of());
  }

  static Stream<Arguments> getMe() {
    return Stream.of(
        Arguments.of("ADMIN", 200),
        Arguments.of("TEACHER", 200),
        Arguments.of("TUTOR", 200),
        Arguments.of("STUDENT", 200),
        Arguments.of("PARENT", 200),
        Arguments.of("anonymous", 401));
  }

  @ParameterizedTest(name = "GET /api/me as {0} -> {1}")
  @MethodSource
  void getMe(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, get("/api/me"))).andExpect(status().is(expectedStatus));
  }

  static Stream<Arguments> getMyStudents() {
    return Stream.of(
        Arguments.of("ADMIN", 403),
        Arguments.of("TEACHER", 403),
        Arguments.of("TUTOR", 200),
        Arguments.of("STUDENT", 403),
        Arguments.of("PARENT", 200),
        Arguments.of("anonymous", 401));
  }

  @ParameterizedTest(name = "GET /api/me/students as {0} -> {1}")
  @MethodSource
  void getMyStudents(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, get("/api/me/students"))).andExpect(status().is(expectedStatus));
  }

  private static MockHttpServletRequestBuilder as(
      String caller, MockHttpServletRequestBuilder request) {
    return "anonymous".equals(caller) ? request : request.with(TestJwt.as(Role.valueOf(caller)));
  }
}
