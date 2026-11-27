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
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
import io.github.samarthshubham.tutor.identity.service.TutorAssignmentResponse;
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

@WebSliceTest(TutorAssignmentController.class)
class TutorAssignmentControllerAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();
  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean RelationshipService relationships;

  @BeforeEach
  void stub() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(ID, "kc", Role.ADMIN, SCHOOL_ID));
    when(relationships.listTutorAssignments(any(), any(), any(), anyInt(), anyInt()))
        .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));
    when(relationships.createTutorAssignment(any(), any(), any()))
        .thenReturn(new TutorAssignmentResponse(ID, ID, ID));
  }

  @Test
  void listAssignments() {
    new AuthorizationMatrix(mvc, () -> get("/api/tutor-assignments"))
        .expect(200, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void createAssignment() {
    new AuthorizationMatrix(
            mvc,
            () ->
                post("/api/tutor-assignments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tutorId\":\"" + ID + "\",\"studentId\":\"" + ID + "\"}"))
        .expect(201, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }

  @Test
  void deleteAssignment() {
    new AuthorizationMatrix(mvc, () -> delete("/api/tutor-assignments/" + ID))
        .expect(204, Role.ADMIN)
        .expect(403, Role.TEACHER, Role.TUTOR, Role.STUDENT, Role.PARENT)
        .verify(401);
  }
}
