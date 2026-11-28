package io.github.samarthshubham.tutor.cms.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.cms.service.LessonService;
import io.github.samarthshubham.tutor.cms.service.LessonView;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(LessonController.class)
class LessonControllerTest {

  private static final UUID ID = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;
  @MockitoBean LessonService lessons;

  private final CurrentUser student = new CurrentUser(ID, "student", Role.STUDENT, ID);

  @BeforeEach
  void stubCurrentUser() {
    when(currentUserProvider.currentUser()).thenReturn(student);
  }

  @Test
  void publishedJsonOmitsDraftFields() throws Exception {
    when(lessons.get(student, ID))
        .thenReturn(
            new LessonView.Published(
                ID,
                ID,
                ID,
                "Title",
                "# Heading\n\nMarkdown",
                1,
                Instant.parse("2026-11-28T06:00:00Z")));
    mvc.perform(get("/api/lessons/" + ID).with(TestJwt.as(Role.STUDENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bodyMarkdown").value("# Heading\n\nMarkdown"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.status").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.reviewComment").doesNotHaveJsonPath())
        .andExpect(jsonPath("$.authorId").doesNotHaveJsonPath());
  }

  @Test
  void validatesTitleTopicBodyAndComment() throws Exception {
    mvc.perform(
            post("/api/courses/" + ID + "/lessons")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"","bodyMarkdown":""}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.title").exists())
        .andExpect(jsonPath("$.fields.topicId").exists())
        .andExpect(jsonPath("$.fields.bodyMarkdown").exists());

    mvc.perform(
            post("/api/lessons/" + ID + "/request-changes")
                .with(TestJwt.as(Role.ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.comment").exists());
    verifyNoInteractions(lessons);
  }

  @Test
  void onlyInReviewIsAValidQueueStatus() throws Exception {
    mvc.perform(get("/api/lessons").param("status", "DRAFT").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.status").exists());
    verifyNoInteractions(lessons);
  }
}
