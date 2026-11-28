package io.github.samarthshubham.tutor.course.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.service.EnrollmentResponse;
import io.github.samarthshubham.tutor.course.service.EnrollmentService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(EnrollmentController.class)
class EnrollmentControllerTest {

  private static final UUID COURSE = UUID.randomUUID();
  private static final UUID STUDENT = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean EnrollmentService enrollments;

  private final CurrentUser teacher =
      new CurrentUser(
          UUID.randomUUID(),
          "kc",
          Role.TEACHER,
          UUID.fromString("00000000-0000-0000-0000-000000000001"));

  @BeforeEach
  void stubTeacher() {
    when(currentUserProvider.currentUser()).thenReturn(teacher);
  }

  @Test
  void listsTheEnrolledStudents() throws Exception {
    when(enrollments.students(teacher, COURSE))
        .thenReturn(List.of(new UserSummary(STUDENT, "Sam Student", Role.STUDENT)));

    mvc.perform(get("/api/courses/" + COURSE + "/students").with(TestJwt.as(Role.TEACHER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(STUDENT.toString()))
        .andExpect(jsonPath("$[0].displayName").value("Sam Student"));
  }

  @Test
  void enrollsWith201() throws Exception {
    when(enrollments.enroll(teacher, COURSE, STUDENT))
        .thenReturn(new EnrollmentResponse(COURSE, STUDENT));

    mvc.perform(enroll(STUDENT))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.courseId").value(COURSE.toString()))
        .andExpect(jsonPath("$.studentId").value(STUDENT.toString()));
  }

  @Test
  void enrollingTwiceIs409() throws Exception {
    when(enrollments.enroll(teacher, COURSE, STUDENT))
        .thenThrow(new ConflictException("This student is already enrolled in this course"));

    mvc.perform(enroll(STUDENT)).andExpect(status().isConflict());
  }

  @Test
  void enrollingANonStudentIs400() throws Exception {
    when(enrollments.enroll(teacher, COURSE, STUDENT))
        .thenThrow(
            new ValidationException(
                "Request is not valid", Map.of("studentId", "must be a STUDENT in your school")));

    mvc.perform(enroll(STUDENT))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.studentId").exists());
  }

  @Test
  void aMissingStudentIdIs400() throws Exception {
    mvc.perform(
            post("/api/courses/" + COURSE + "/enrollments")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.studentId").exists());
  }

  @Test
  void removesWith204() throws Exception {
    mvc.perform(
            delete("/api/courses/" + COURSE + "/enrollments/" + STUDENT)
                .with(TestJwt.as(Role.TEACHER)))
        .andExpect(status().isNoContent());
    verify(enrollments).remove(teacher, COURSE, STUDENT);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder enroll(
      UUID student) {
    return post("/api/courses/" + COURSE + "/enrollments")
        .with(TestJwt.as(Role.TEACHER))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"studentId\":\"" + student + "\"}");
  }
}
