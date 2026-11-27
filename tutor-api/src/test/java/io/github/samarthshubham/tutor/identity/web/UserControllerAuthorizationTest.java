package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.UserResponse;
import io.github.samarthshubham.tutor.identity.service.UserService;
import io.github.samarthshubham.tutor.support.AuthorizationMatrix;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebSliceTest(UserController.class)
class UserControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean UserService users;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.ADMIN, SCHOOL_ID));
    when(users.search(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    when(users.getInSchool(any(), any()))
        .thenReturn(new UserResponse(ID, "u", "U", null, Role.STUDENT, SCHOOL_ID));
  }

  @Test
  void listUsers() {
    new AuthorizationMatrix(mvc, () -> get("/api/users"))
        .expect(200, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void getUser() {
    new AuthorizationMatrix(mvc, () -> get("/api/users/" + ID))
        .expect(200, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }
}
