package io.github.samarthshubham.tutor.course.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.github.samarthshubham.tutor.course.service.TopicResponse;
import io.github.samarthshubham.tutor.course.service.TopicService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.AuthorizationMatrix;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(TopicController.class)
class TopicControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean TopicService topics;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(
            new CurrentUser(
                ID, "kc", Role.ADMIN, UUID.fromString("00000000-0000-0000-0000-000000000001")));
    when(topics.add(any(), any(), any(), any())).thenReturn(new TopicResponse(ID, "n", 1));
    when(topics.update(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
        .thenReturn(new TopicResponse(ID, "n", 1));
  }

  @Test
  void addTopic() {
    new AuthorizationMatrix(
            mvc,
            () ->
                post("/api/courses/" + ID + "/topics")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Cells\"}"))
        .expect(201, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void updateTopic() {
    new AuthorizationMatrix(
            mvc,
            () ->
                put("/api/topics/" + ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Cells\",\"position\":1}"))
        .expect(200, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void deleteTopic() {
    new AuthorizationMatrix(mvc, () -> delete("/api/topics/" + ID))
        .expect(204, Role.ADMIN, Role.TEACHER)
        .expect(403, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }
}
