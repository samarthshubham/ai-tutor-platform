package io.github.samarthshubham.tutor.identity.web;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
import io.github.samarthshubham.tutor.identity.service.TutorAssignmentResponse;
import io.github.samarthshubham.tutor.support.TestJwt;
import io.github.samarthshubham.tutor.support.WebSliceTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebSliceTest(TutorAssignmentController.class)
class TutorAssignmentControllerTest {

  private static final UUID SCHOOL_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TUTOR_ID = UUID.randomUUID();
  private static final UUID STUDENT_ID = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired CurrentUserProvider currentUserProvider;

  @MockitoBean RelationshipService relationships;

  @BeforeEach
  void stubAdmin() {
    when(currentUserProvider.currentUser())
        .thenReturn(new CurrentUser(UUID.randomUUID(), "kc-admin1", Role.ADMIN, SCHOOL_ID));
  }

  @Test
  void createsAnAssignmentWith201() throws Exception {
    UUID id = UUID.randomUUID();
    when(relationships.createTutorAssignment(TUTOR_ID, STUDENT_ID, SCHOOL_ID))
        .thenReturn(new TutorAssignmentResponse(id, TUTOR_ID, STUDENT_ID));

    mvc.perform(create(body(TUTOR_ID, STUDENT_ID)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(id.toString()))
        .andExpect(jsonPath("$.tutorId").value(TUTOR_ID.toString()))
        .andExpect(jsonPath("$.studentId").value(STUDENT_ID.toString()));
  }

  @Test
  void aDuplicateIs409() throws Exception {
    when(relationships.createTutorAssignment(TUTOR_ID, STUDENT_ID, SCHOOL_ID))
        .thenThrow(new ConflictException("This tutor is already assigned to this student"));

    mvc.perform(create(body(TUTOR_ID, STUDENT_ID)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void aWrongRoleIs400NamingTheField() throws Exception {
    when(relationships.createTutorAssignment(TUTOR_ID, STUDENT_ID, SCHOOL_ID))
        .thenThrow(
            new ValidationException(
                "Request is not valid", Map.of("tutorId", "must be a TUTOR in your school")));

    mvc.perform(create(body(TUTOR_ID, STUDENT_ID)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.tutorId").value("must be a TUTOR in your school"));
  }

  @Test
  void anUnknownUserIs404() throws Exception {
    when(relationships.createTutorAssignment(TUTOR_ID, STUDENT_ID, SCHOOL_ID))
        .thenThrow(new NotFoundException("User not found"));

    mvc.perform(create(body(TUTOR_ID, STUDENT_ID)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void aMissingIdIs400NamingTheField() throws Exception {
    mvc.perform(create("{\"tutorId\":\"" + TUTOR_ID + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.studentId").exists());
    verifyNoInteractions(relationships);
  }

  @Test
  void listsAssignmentsFilteredByStudent() throws Exception {
    UUID id = UUID.randomUUID();
    when(relationships.listTutorAssignments(SCHOOL_ID, null, STUDENT_ID, 0, 20))
        .thenReturn(
            new PageResponse<>(
                List.of(new TutorAssignmentResponse(id, TUTOR_ID, STUDENT_ID)), 0, 20, 1, 1));

    mvc.perform(get("/api/tutor-assignments?studentId=" + STUDENT_ID).with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(id.toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  void deletesWith204AndAMissingAssignmentIs404() throws Exception {
    UUID id = UUID.randomUUID();
    mvc.perform(delete("/api/tutor-assignments/" + id).with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isNoContent());
    verify(relationships).deleteTutorAssignment(id, SCHOOL_ID);

    UUID missing = UUID.randomUUID();
    doThrow(new NotFoundException("Tutor assignment not found"))
        .when(relationships)
        .deleteTutorAssignment(missing, SCHOOL_ID);
    mvc.perform(delete("/api/tutor-assignments/" + missing).with(TestJwt.as(Role.ADMIN)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  private static String body(UUID tutorId, UUID studentId) {
    return "{\"tutorId\":\"" + tutorId + "\",\"studentId\":\"" + studentId + "\"}";
  }

  private static MockHttpServletRequestBuilder create(String json) {
    return post("/api/tutor-assignments")
        .with(TestJwt.as(Role.ADMIN))
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }
}
