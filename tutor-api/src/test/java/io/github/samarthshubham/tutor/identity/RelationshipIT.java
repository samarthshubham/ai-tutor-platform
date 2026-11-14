package io.github.samarthshubham.tutor.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.samarthshubham.tutor.identity.api.RelationshipQuery;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import io.github.samarthshubham.tutor.support.IntegrationTest;
import io.github.samarthshubham.tutor.support.TestJwt;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class RelationshipIT extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RelationshipQuery relationships;

  private String run;
  private String adminToken;

  @BeforeEach
  void signInAdmin() throws Exception {
    run = UUID.randomUUID().toString().substring(0, 8);
    adminToken = TestJwt.bearer("admin-" + run, "ADMIN");
    signIn(adminToken);
  }

  @Test
  void guardianLinksAreCreatedOnceValidatedListedAndDeleted() throws Exception {
    UUID parent = signIn("parent", "PARENT");
    UUID student = signIn("student", "STUDENT");
    UUID teacher = signIn("teacher", "TEACHER");

    String id =
        JsonPath.read(
            createLink("/api/guardian-links", "parentId", parent, student)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(parent.toString()))
                .andExpect(jsonPath("$.studentId").value(student.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    createLink("/api/guardian-links", "parentId", parent, student)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    createLink("/api/guardian-links", "parentId", teacher, student)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.parentId").value("must be a PARENT in your school"));
    createLink("/api/guardian-links", "parentId", UUID.randomUUID(), student)
        .andExpect(status().isNotFound());

    mvc.perform(admin(get("/api/guardian-links?studentId=" + student)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(id));

    mvc.perform(admin(delete("/api/guardian-links/" + id))).andExpect(status().isNoContent());
    mvc.perform(admin(delete("/api/guardian-links/" + id))).andExpect(status().isNotFound());
    assertThat(relationships.studentIdsOfParent(parent)).isEmpty();
  }

  @Test
  void tutorAssignmentsFollowTheSameRules() throws Exception {
    UUID tutor = signIn("tutor", "TUTOR");
    UUID student = signIn("student", "STUDENT");
    UUID parent = signIn("parent", "PARENT");

    String id =
        JsonPath.read(
            createLink("/api/tutor-assignments", "tutorId", tutor, student)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tutorId").value(tutor.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    createLink("/api/tutor-assignments", "tutorId", tutor, student)
        .andExpect(status().isConflict());
    createLink("/api/tutor-assignments", "tutorId", parent, student)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.tutorId").exists());
    createLink("/api/tutor-assignments", "tutorId", tutor, parent)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.studentId").exists());

    mvc.perform(admin(get("/api/tutor-assignments?tutorId=" + tutor)))
        .andExpect(jsonPath("$.totalElements").value(1));

    mvc.perform(admin(delete("/api/tutor-assignments/" + id))).andExpect(status().isNoContent());
    mvc.perform(admin(delete("/api/tutor-assignments/" + id))).andExpect(status().isNotFound());
  }

  @Test
  void relationshipQueryAndMyStudentsWorkInBothDirections() throws Exception {
    String parentToken = TestJwt.bearer("parent-" + run, "PARENT");
    String tutorToken = TestJwt.bearer("tutor-" + run, "TUTOR");
    String lonelyTutorToken = TestJwt.bearer("tutor2-" + run, "TUTOR");
    UUID parent = signIn(parentToken);
    UUID tutor = signIn(tutorToken);
    signIn(lonelyTutorToken);
    UUID first = signIn("student-b", "STUDENT");
    UUID second = signIn("student-a", "STUDENT");
    for (UUID student : List.of(first, second)) {
      createLink("/api/guardian-links", "parentId", parent, student)
          .andExpect(status().isCreated());
      createLink("/api/tutor-assignments", "tutorId", tutor, student)
          .andExpect(status().isCreated());
    }

    assertThat(relationships.studentIdsOfParent(parent)).containsExactlyInAnyOrder(first, second);
    assertThat(relationships.studentIdsOfTutor(tutor)).containsExactlyInAnyOrder(first, second);
    assertThat(relationships.parentIdsOfStudent(first)).containsExactly(parent);
    assertThat(relationships.tutorIdsOfStudent(second)).containsExactly(tutor);
    assertThat(relationships.findUser(first))
        .contains(new UserSummary(first, "Test student-b-" + run, Role.STUDENT));
    assertThat(relationships.findUsers(List.of(first, second)))
        .extracting(UserSummary::id)
        .containsExactly(second, first);

    for (String token : List.of(parentToken, tutorToken)) {
      mvc.perform(get("/api/me/students").header(HttpHeaders.AUTHORIZATION, token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value(second.toString()))
          .andExpect(jsonPath("$[1].id").value(first.toString()));
    }
    mvc.perform(get("/api/me/students").header(HttpHeaders.AUTHORIZATION, lonelyTutorToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void usersAreFilteredByRoleAndTextAndSortedByDisplayName() throws Exception {
    UUID second = signIn("sa-zed", "STUDENT");
    UUID first = signIn("sa-amy", "STUDENT");
    signIn("sa-tutor", "TUTOR");

    mvc.perform(
            admin(
                get("/api/users")
                    .param("role", "STUDENT")
                    .param("q", run.toUpperCase(Locale.ROOT))
                    .param("size", "10")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.content[0].id").value(first.toString()))
        .andExpect(jsonPath("$.content[1].id").value(second.toString()))
        .andExpect(jsonPath("$.content[0].role").value("STUDENT"));
    mvc.perform(admin(get("/api/users").param("q", "TEST SA-ZED-" + run)))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(second.toString()));
    mvc.perform(admin(get("/api/users").param("q", "%")))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(admin(get("/api/users/" + first)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("sa-amy-" + run));
    mvc.perform(admin(get("/api/users/" + UUID.randomUUID()))).andExpect(status().isNotFound());
  }

  @Test
  void theDatabaseRejectsDuplicatePairsAndUnknownUsers() throws Exception {
    UUID parent = signIn("parent", "PARENT");
    UUID student = signIn("student", "STUDENT");
    String insertLink =
        "insert into guardian_link (id, parent_id, student_id, created_at) values (?, ?, ?, now())";
    jdbc.update(insertLink, UUID.randomUUID(), parent, student);

    assertThatThrownBy(() -> jdbc.update(insertLink, UUID.randomUUID(), parent, student))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update(
                "insert into tutor_assignment (id, tutor_id, student_id, created_at)"
                    + " values (?, ?, ?, now())",
                UUID.randomUUID(),
                UUID.randomUUID(),
                student))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private ResultActions createLink(String path, String field, UUID other, UUID student)
      throws Exception {
    return mvc.perform(
        admin(post(path))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"%s\":\"%s\",\"studentId\":\"%s\"}".formatted(field, other, student)));
  }

  private MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, adminToken);
  }

  private UUID signIn(String prefix, String role) throws Exception {
    return signIn(TestJwt.bearer(prefix + "-" + run, role));
  }

  private UUID signIn(String token) throws Exception {
    String body =
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(body, "$.id"));
  }
}
