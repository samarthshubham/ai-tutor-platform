package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
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

@WebSliceTest(MeController.class)
class MeControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean UserService users;
  @MockitoBean RelationshipService relationships;

  @BeforeEach
  void stubUser() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.STUDENT, SCHOOL_ID));
    when(users.get(any()))
        .thenReturn(new UserResponse(ID, "u", "U", "u@tutor.local", Role.STUDENT, SCHOOL_ID));
    when(relationships.studentsOf(any())).thenReturn(List.of());
  }

  @Test
  void getMe() {
    new AuthorizationMatrix(mvc, () -> get("/api/me")).expect(200, Role.values()).verify(401);
  }

  @Test
  void getMyStudents() {
    new AuthorizationMatrix(mvc, () -> get("/api/me/students"))
        .expect(200, Role.TUTOR, Role.PARENT)
        .expect(403, Role.ADMIN, Role.TEACHER, Role.STUDENT)
        .verify(401);
  }
}
