package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
import io.github.samarthshubham.tutor.identity.service.TutorAssignmentResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebSliceTest(TutorAssignmentController.class)
class TutorAssignmentControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean RelationshipService relationships;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.ADMIN, SCHOOL_ID));
    when(relationships.listTutorAssignments(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    when(relationships.createTutorAssignment(any(), any(), any()))
        .thenReturn(new TutorAssignmentResponse(ID, ID, ID));
  }

  static Stream<Arguments> adminOnly(int adminStatus) {
    return Stream.of(
        Arguments.of("ADMIN", adminStatus),
        Arguments.of("TEACHER", 403),
        Arguments.of("TUTOR", 403),
        Arguments.of("STUDENT", 403),
        Arguments.of("PARENT", 403),
        Arguments.of("anonymous", 401));
  }

  static Stream<Arguments> listAssignments() {
    return adminOnly(200);
  }

  static Stream<Arguments> createAssignment() {
    return adminOnly(201);
  }

  static Stream<Arguments> deleteAssignment() {
    return adminOnly(204);
  }

  @ParameterizedTest(name = "GET /api/tutor-assignments as {0} -> {1}")
  @MethodSource
  void listAssignments(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, get("/api/tutor-assignments"))).andExpect(status().is(expectedStatus));
  }

  @ParameterizedTest(name = "POST /api/tutor-assignments as {0} -> {1}")
  @MethodSource
  void createAssignment(String caller, int expectedStatus) throws Exception {
    String body = "{\"tutorId\":\"" + ID + "\",\"studentId\":\"" + ID + "\"}";
    mvc.perform(
            as(
                caller,
                post("/api/tutor-assignments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)))
        .andExpect(status().is(expectedStatus));
  }

  @ParameterizedTest(name = "DELETE /api/tutor-assignments/<id> as {0} -> {1}")
  @MethodSource
  void deleteAssignment(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, delete("/api/tutor-assignments/" + ID)))
        .andExpect(status().is(expectedStatus));
  }

  private static MockHttpServletRequestBuilder as(
      String caller, MockHttpServletRequestBuilder request) {
    return "anonymous".equals(caller) ? request : request.with(TestJwt.as(Role.valueOf(caller)));
  }
}
