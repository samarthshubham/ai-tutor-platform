package io.github.samarthshubham.tutor.course.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.samarthshubham.tutor.course.service.EnrollmentResponse;
import io.github.samarthshubham.tutor.course.service.EnrollmentService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.AuthorizationMatrix;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(EnrollmentController.class)
class EnrollmentControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean EnrollmentService enrollments;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(
            new CurrentUser(
                ID, "kc", Role.ADMIN, UUID.fromString("00000000-0000-0000-0000-000000000001")));
    when(enrollments.students(any(), any())).thenReturn(List.of());
    when(enrollments.enroll(any(), any(), any())).thenReturn(new EnrollmentResponse(ID, ID));
  }

  @Test
  void listStudents() {
    new AuthorizationMatrix(mvc, () -> get("/api/courses/" + ID + "/students"))
        .expect(200, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void searchStudents() {
    new AuthorizationMatrix(
            mvc, () -> get("/api/courses/" + ID + "/student-search").param("q", "Sam"))
        .expect(200, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void enroll() {
    new AuthorizationMatrix(
            mvc,
            () ->
                post("/api/courses/" + ID + "/enrollments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"studentId\":\"" + ID + "\"}"))
        .expect(201, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void removeEnrollment() {
    new AuthorizationMatrix(mvc, () -> delete("/api/courses/" + ID + "/enrollments/" + ID))
        .expect(204, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }
}
