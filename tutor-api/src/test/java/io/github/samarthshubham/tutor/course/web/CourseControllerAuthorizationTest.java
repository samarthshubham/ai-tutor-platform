package io.github.samarthshubham.tutor.course.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.course.api.CourseStatus;
import io.github.samarthshubham.tutor.course.service.CourseResponse;
import io.github.samarthshubham.tutor.course.service.CourseService;
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

@WebSliceTest(CourseController.class)
class CourseControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String BODY =
      "{\"title\":\"Grade 8 Science\",\"subject\":\"Science\",\"gradeLevel\":8}";

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean CourseService courses;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.ADMIN, SCHOOL));
    CourseResponse response =
        new CourseResponse(ID, "t", "s", 8, null, ID, "n", CourseStatus.ACTIVE, List.of());
    when(courses.list(any(), anyInt(), anyInt()))
        .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    when(courses.get(any(), any())).thenReturn(response);
    when(courses.create(any(), any(), any(), anyInt(), any(), any())).thenReturn(response);
    when(courses.update(any(), any(), any(), any(), anyInt(), any())).thenReturn(response);
    when(courses.archive(any(), any())).thenReturn(response);
  }

  @Test
  void listCourses() {
    new AuthorizationMatrix(mvc, () -> get("/api/courses"))
        .expect(200, Role.ADMIN, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void getCourse() {
    new AuthorizationMatrix(mvc, () -> get("/api/courses/" + ID))
        .expect(200, Role.ADMIN, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void createCourse() {
    new AuthorizationMatrix(
            mvc, () -> post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(BODY))
        .expect(201, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void updateCourse() {
    new AuthorizationMatrix(
            mvc,
            () -> put("/api/courses/" + ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
        .expect(200, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void archiveCourse() {
    new AuthorizationMatrix(mvc, () -> post("/api/courses/" + ID + "/archive"))
        .expect(200, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }
}
