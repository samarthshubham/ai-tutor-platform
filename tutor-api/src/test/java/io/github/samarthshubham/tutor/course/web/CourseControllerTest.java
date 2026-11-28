package io.github.samarthshubham.tutor.course.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.api.CourseStatus;
import io.github.samarthshubham.tutor.course.service.CourseResponse;
import io.github.samarthshubham.tutor.course.service.CourseService;
import io.github.samarthshubham.tutor.course.service.TopicResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
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

@WebSliceTest(CourseController.class)
class CourseControllerTest {

  private static final UUID SCHOOL = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID COURSE = UUID.randomUUID();
  private static final String BODY =
      "{\"title\":\"Grade 8 Science\",\"subject\":\"Science\",\"gradeLevel\":8}";

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean CourseService courses;

  private final CurrentUser teacher =
      new CurrentUser(UUID.randomUUID(), "kc", Role.TEACHER, SCHOOL);

  @BeforeEach
  void stubTeacher() {
    when(currentUserProvider.currentUser()).thenReturn(teacher);
  }

  private CourseResponse response() {
    return new CourseResponse(
        COURSE,
        "Grade 8 Science",
        "Science",
        8,
        null,
        teacher.id(),
        "Tara Teacher",
        CourseStatus.ACTIVE,
        List.of(new TopicResponse(UUID.randomUUID(), "Cells", 1)));
  }

  @Test
  void createsACourseWith201() throws Exception {
    when(courses.create(teacher, "Grade 8 Science", "Science", 8, null, null))
        .thenReturn(response());

    mvc.perform(
            post("/api/courses")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(COURSE.toString()))
        .andExpect(jsonPath("$.teacherName").value("Tara Teacher"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.topics[0].name").value("Cells"));
  }

  @Test
  void anInvalidBodyIs400NamingTheFields() throws Exception {
    mvc.perform(
            post("/api/courses")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"subject\":\"S\",\"gradeLevel\":13}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.title").exists())
        .andExpect(jsonPath("$.fields.gradeLevel").exists());
    verifyNoInteractions(courses);
  }

  @Test
  void aBusinessValidationFailureNamesTheField() throws Exception {
    when(courses.create(teacher, "Grade 8 Science", "Science", 8, null, null))
        .thenThrow(
            new ValidationException("Request is not valid", Map.of("teacherId", "is required")));

    mvc.perform(
            post("/api/courses")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.teacherId").value("is required"));
  }

  @Test
  void listsAPage() throws Exception {
    when(courses.list(teacher, 0, 20))
        .thenReturn(new PageResponse<>(List.of(response()), 0, 20, 1, 1));

    mvc.perform(get("/api/courses").with(TestJwt.as(Role.TEACHER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].title").value("Grade 8 Science"));
  }

  @Test
  void aHiddenCourseIs404() throws Exception {
    when(courses.get(teacher, COURSE)).thenThrow(new NotFoundException("Course not found"));

    mvc.perform(get("/api/courses/" + COURSE).with(TestJwt.as(Role.TEACHER)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void updatesACourse() throws Exception {
    when(courses.update(teacher, COURSE, "Grade 8 Science", "Science", 8, null))
        .thenReturn(response());

    mvc.perform(
            put("/api/courses/" + COURSE)
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(COURSE.toString()));
  }

  @Test
  void archivingAnArchivedCourseIs409() throws Exception {
    when(courses.archive(teacher, COURSE)).thenThrow(new ConflictException("Course is archived"));

    mvc.perform(post("/api/courses/" + COURSE + "/archive").with(TestJwt.as(Role.TEACHER)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    verify(courses).archive(teacher, COURSE);
  }
}
