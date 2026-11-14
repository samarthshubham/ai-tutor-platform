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
import io.github.samarthshubham.tutor.identity.service.GuardianLinkResponse;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
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

@WebSliceTest(GuardianLinkController.class)
class GuardianLinkControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean RelationshipService relationships;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.ADMIN, SCHOOL_ID));
    when(relationships.listGuardianLinks(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    when(relationships.createGuardianLink(any(), any(), any()))
        .thenReturn(new GuardianLinkResponse(ID, ID, ID));
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

  static Stream<Arguments> listLinks() {
    return adminOnly(200);
  }

  static Stream<Arguments> createLink() {
    return adminOnly(201);
  }

  static Stream<Arguments> deleteLink() {
    return adminOnly(204);
  }

  @ParameterizedTest(name = "GET /api/guardian-links as {0} -> {1}")
  @MethodSource
  void listLinks(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, get("/api/guardian-links"))).andExpect(status().is(expectedStatus));
  }

  @ParameterizedTest(name = "POST /api/guardian-links as {0} -> {1}")
  @MethodSource
  void createLink(String caller, int expectedStatus) throws Exception {
    String body = "{\"parentId\":\"" + ID + "\",\"studentId\":\"" + ID + "\"}";
    mvc.perform(
            as(
                caller,
                post("/api/guardian-links").contentType(MediaType.APPLICATION_JSON).content(body)))
        .andExpect(status().is(expectedStatus));
  }

  @ParameterizedTest(name = "DELETE /api/guardian-links/<id> as {0} -> {1}")
  @MethodSource
  void deleteLink(String caller, int expectedStatus) throws Exception {
    mvc.perform(as(caller, delete("/api/guardian-links/" + ID)))
        .andExpect(status().is(expectedStatus));
  }

  private static MockHttpServletRequestBuilder as(
      String caller, MockHttpServletRequestBuilder request) {
    return "anonymous".equals(caller) ? request : request.with(TestJwt.as(Role.valueOf(caller)));
  }
}
