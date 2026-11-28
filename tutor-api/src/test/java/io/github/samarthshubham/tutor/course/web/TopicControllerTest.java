package io.github.samarthshubham.tutor.course.web;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.course.service.TopicResponse;
import io.github.samarthshubham.tutor.course.service.TopicService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(TopicController.class)
class TopicControllerTest {

  private static final UUID COURSE = UUID.randomUUID();
  private static final UUID TOPIC = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean TopicService topics;

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
  void addsATopicWith201() throws Exception {
    when(topics.add(teacher, COURSE, "Cells", 2)).thenReturn(new TopicResponse(TOPIC, "Cells", 2));

    mvc.perform(
            post("/api/courses/" + COURSE + "/topics")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cells\",\"position\":2}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(TOPIC.toString()))
        .andExpect(jsonPath("$.position").value(2));
  }

  @Test
  void aDuplicateNameIs409() throws Exception {
    when(topics.add(teacher, COURSE, "Cells", null))
        .thenThrow(new ConflictException("This course already has a topic with this name"));

    mvc.perform(
            post("/api/courses/" + COURSE + "/topics")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cells\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void aBlankNameIs400() throws Exception {
    mvc.perform(
            post("/api/courses/" + COURSE + "/topics")
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.name").exists());
  }

  @Test
  void updateNeedsAPosition() throws Exception {
    mvc.perform(
            put("/api/topics/" + TOPIC)
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Cells\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.position").exists());
  }

  @Test
  void updatesATopic() throws Exception {
    when(topics.update(teacher, TOPIC, "Atoms", 1))
        .thenReturn(new TopicResponse(TOPIC, "Atoms", 1));

    mvc.perform(
            put("/api/topics/" + TOPIC)
                .with(TestJwt.as(Role.TEACHER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Atoms\",\"position\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Atoms"));
  }

  @Test
  void deletesWith204AndATopicInUseIs409() throws Exception {
    mvc.perform(delete("/api/topics/" + TOPIC).with(TestJwt.as(Role.TEACHER)))
        .andExpect(status().isNoContent());
    verify(topics).delete(teacher, TOPIC);

    UUID used = UUID.randomUUID();
    doThrow(new ConflictException("Topic is in use")).when(topics).delete(teacher, used);
    mvc.perform(delete("/api/topics/" + used).with(TestJwt.as(Role.TEACHER)))
        .andExpect(status().isConflict());
  }
}
