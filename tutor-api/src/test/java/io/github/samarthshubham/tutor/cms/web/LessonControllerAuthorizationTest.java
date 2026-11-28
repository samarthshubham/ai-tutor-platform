package io.github.samarthshubham.tutor.cms.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import io.github.samarthshubham.tutor.cms.service.LessonService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.AuthorizationMatrix;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebSliceTest(LessonController.class)
class LessonControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final String LESSON = "/api/lessons/" + ID;
  private static final String COURSE = "/api/courses/" + ID + "/lessons";
  private static final String DRAFT =
      """
      {"title":"Cells","topicId":"%s","bodyMarkdown":"Body"}
      """
          .formatted(ID);

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;
  @MockitoBean LessonService lessons;

  @BeforeEach
  void stubCurrentUser() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "admin", Role.ADMIN, ID));
  }

  @Test
  void allRolesCanReadCourseLessonsAndLessonDetails() {
    check(() -> get(COURSE), 200, Role.values());
    check(() -> get(LESSON), 200, Role.values());
  }

  @Test
  void onlyTeachersCanCreateEditSubmitAndRevise() {
    check(() -> json(post(COURSE), DRAFT), 201, Role.TEACHER);
    check(() -> json(put(LESSON), DRAFT), 200, Role.TEACHER);
    check(() -> post(LESSON + "/submit"), 200, Role.TEACHER);
    check(() -> post(LESSON + "/revise"), 200, Role.TEACHER);
  }

  @Test
  void onlyAdminsCanReview() {
    check(() -> get("/api/lessons").param("status", "IN_REVIEW"), 200, Role.ADMIN);
    check(() -> post(LESSON + "/publish"), 200, Role.ADMIN);
    check(
        () -> json(post(LESSON + "/request-changes"), "{\"comment\":\"Example\"}"),
        200,
        Role.ADMIN);
  }

  @Test
  void adminsAndTeachersCanArchiveAndListHistory() {
    check(() -> post(LESSON + "/archive"), 200, Role.ADMIN, Role.TEACHER);
    check(() -> get(LESSON + "/versions"), 200, Role.ADMIN, Role.TEACHER);
  }

  private void check(
      Supplier<MockHttpServletRequestBuilder> request, int allowedStatus, Role... allowed) {
    List<Role> roles = List.of(allowed);
    Role[] denied =
        Arrays.stream(Role.values()).filter(role -> !roles.contains(role)).toArray(Role[]::new);
    new AuthorizationMatrix(mvc, request)
        .expect(allowedStatus, allowed)
        .expect(403, denied)
        .verify(401);
  }

  private static MockHttpServletRequestBuilder json(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }
}
