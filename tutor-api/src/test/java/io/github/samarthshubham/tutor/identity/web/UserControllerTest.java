package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.UserResponse;
import io.github.samarthshubham.tutor.identity.service.UserService;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(UserController.class)
class UserControllerTest {

  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean UserService users;

  @BeforeEach
  void stubAdmin() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(UUID.randomUUID(), "kc-admin1", Role.ADMIN, SCHOOL_ID));
  }

  @Test
  void listsAPageOfMatchingUsers() throws Exception {
    UUID id = UUID.randomUUID();
    when(users.search(SCHOOL_ID, Role.STUDENT, "sa", 0, 20))
        .thenReturn(
            new PageResponse<>(
                List.of(
                    new UserResponse(
                        id,
                        "student1",
                        "Sam Student",
                        "student1@tutor.local",
                        Role.STUDENT,
                        SCHOOL_ID)),
                0,
                20,
                1,
                1));

    mvc.perform(get("/api/users?role=STUDENT&q=sa").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(id.toString()))
        .andExpect(jsonPath("$.content[0].username").value("student1"))
        .andExpect(jsonPath("$.content[0].role").value("STUDENT"))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.totalPages").value(1));
  }

  @Test
  void anUnknownRoleOrAnOversizedPageIsAValidationError() throws Exception {
    mvc.perform(get("/api/users?role=WIZARD").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.role").exists());
    mvc.perform(get("/api/users?size=101").with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.size").exists());
    verifyNoInteractions(users);
  }

  @Test
  void returnsOneUser() throws Exception {
    UUID id = UUID.randomUUID();
    when(users.getInSchool(id, SCHOOL_ID))
        .thenReturn(new UserResponse(id, "tutor1", "Tom Tutor", null, Role.TUTOR, SCHOOL_ID));

    mvc.perform(get("/api/users/" + id).with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.displayName").value("Tom Tutor"))
        .andExpect(jsonPath("$.schoolId").value(SCHOOL_ID.toString()));
  }

  @Test
  void anUnknownOrHiddenUserIs404() throws Exception {
    when(users.getInSchool(any(), any())).thenThrow(new NotFoundException("User not found"));

    mvc.perform(get("/api/users/" + UUID.randomUUID()).with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
