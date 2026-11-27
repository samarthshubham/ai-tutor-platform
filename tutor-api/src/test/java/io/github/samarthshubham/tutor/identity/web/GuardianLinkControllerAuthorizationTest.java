package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.GuardianLinkResponse;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
import io.github.samarthshubham.tutor.support.AuthorizationMatrix;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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

  @Test
  void listLinks() {
    new AuthorizationMatrix(mvc, () -> get("/api/guardian-links"))
        .expect(200, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void createLink() {
    new AuthorizationMatrix(
            mvc,
            () ->
                post("/api/guardian-links")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"parentId\":\"" + ID + "\",\"studentId\":\"" + ID + "\"}"))
        .expect(201, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void deleteLink() {
    new AuthorizationMatrix(mvc, () -> delete("/api/guardian-links/" + ID))
        .expect(204, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }
}
