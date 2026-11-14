package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.ForbiddenException;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
import io.github.samarthshubham.tutor.identity.service.UserResponse;
import io.github.samarthshubham.tutor.identity.service.UserService;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(MeController.class)
class MeControllerTest {

  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean UserService users;
  @MockitoBean RelationshipService relationships;

  @Test
  void returnsTheCurrentUserResolvedFromTheToken() throws Exception {
    UUID id = UUID.randomUUID();
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(id, "kc-teacher1", Role.TEACHER, SCHOOL_ID));
    when(users.get(id))
        .thenReturn(
            new UserResponse(
                id, "teacher1", "Tara Teacher", "teacher1@tutor.local", Role.TEACHER, SCHOOL_ID));

    mvc.perform(get("/api/me").with(TestJwt.as("teacher1", Role.TEACHER)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.username").value("teacher1"))
        .andExpect(jsonPath("$.displayName").value("Tara Teacher"))
        .andExpect(jsonPath("$.email").value("teacher1@tutor.local"))
        .andExpect(jsonPath("$.role").value("TEACHER"))
        .andExpect(jsonPath("$.schoolId").value(SCHOOL_ID.toString()));
  }

  @Test
  void withoutATokenIs401Unauthenticated() throws Exception {
    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").value("Authentication required"))
        .andExpect(jsonPath("$.fields").doesNotExist());
    verifyNoInteractions(currentUserProvider, users);
  }

  @Test
  void aTokenWithoutOneOfTheFiveRolesIs403Forbidden() throws Exception {
    mvc.perform(get("/api/me").with(TestJwt.withRealmRoles("nobody", "default-roles-tutor")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Access denied"));
    verifyNoInteractions(currentUserProvider, users);
  }

  @Test
  void aTokenWithTwoOfTheFiveRolesIs403Forbidden() throws Exception {
    mvc.perform(get("/api/me").with(TestJwt.withRealmRoles("both", "ADMIN", "TEACHER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    verifyNoInteractions(currentUserProvider, users);
  }

  @Test
  void aForbiddenExceptionFromTheProviderIs403() throws Exception {
    when(currentUserProvider.currentUser()).thenThrow(new ForbiddenException("Access denied"));

    mvc.perform(get("/api/me").with(TestJwt.as(Role.STUDENT)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void myStudentsReturnsTheLinkedStudentsAsSummaries() throws Exception {
    CurrentUser parent = new CurrentUser(UUID.randomUUID(), "kc-parent1", Role.PARENT, SCHOOL_ID);
    UUID studentId = UUID.randomUUID();
    when(currentUserProvider.currentUser()).thenReturn(parent);
    when(relationships.studentsOf(parent))
        .thenReturn(List.of(new UserSummary(studentId, "Sam Student", Role.STUDENT)));

    mvc.perform(get("/api/me/students").with(TestJwt.as("parent1", Role.PARENT)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(studentId.toString()))
        .andExpect(jsonPath("$[0].displayName").value("Sam Student"))
        .andExpect(jsonPath("$[0].role").value("STUDENT"))
        .andExpect(jsonPath("$[0].email").doesNotExist());
  }

  @Test
  void myStudentsIsAnEmptyListWhenThereAreNone() throws Exception {
    CurrentUser tutor = new CurrentUser(UUID.randomUUID(), "kc-tutor1", Role.TUTOR, SCHOOL_ID);
    when(currentUserProvider.currentUser()).thenReturn(tutor);
    when(relationships.studentsOf(tutor)).thenReturn(List.of());

    mvc.perform(get("/api/me/students").with(TestJwt.as("tutor1", Role.TUTOR)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }
}
