package io.github.samarthshubham.tutor.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.samarthshubham.tutor.cms.api.LessonArchived;
import io.github.samarthshubham.tutor.cms.api.LessonPublished;
import io.github.samarthshubham.tutor.cms.api.LessonQuery;
import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import io.github.samarthshubham.tutor.cms.domain.Lesson;
import io.github.samarthshubham.tutor.cms.service.LessonService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.IntegrationTest;
import io.github.samarthshubham.tutor.support.TestJwt;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@RecordApplicationEvents
@Import(LessonWorkflowIT.EventConfig.class)
class LessonWorkflowIT extends IntegrationTest {

  private static final UUID SCHOOL = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String INITIAL_BODY = "# Photosynthesis\n\n  Plants use light.  ";
  private static final String REVISED_BODY = "# New draft\n\n  More detail.  ";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ApplicationEvents events;
  @Autowired EventProbe probe;
  @Autowired LessonQuery lessonQuery;
  @Autowired LessonService lessonService;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired EntityManager entityManager;

  private String scope;
  private String teacher;
  private String otherTeacher;
  private String admin;
  private String student;
  private String tutor;
  private String parent;
  private String course;
  private String topic;
  private UUID adminId;
  private UUID studentId;

  @BeforeEach
  void prepareCourse() throws Exception {
    probe.publications.clear();
    probe.archives.clear();
    scope = UUID.randomUUID().toString().substring(0, 8);
    teacher = token("teacher", "TEACHER");
    otherTeacher = token("other-teacher", "TEACHER");
    admin = token("admin", "ADMIN");
    student = token("student", "STUDENT");
    tutor = token("tutor", "TUTOR");
    parent = token("parent", "PARENT");
    signIn(teacher);
    signIn(otherTeacher);
    adminId = signIn(admin);
    studentId = signIn(student);
    course = createCourse(teacher);
    topic = addTopic(teacher, course, "Plants");
  }

  @Test
  void staffWorkOnDraftsAndReadersSeeOnlyTheImmutablePublishedVersion() throws Exception {
    enroll();
    link("/api/tutor-assignments", "tutorId", signIn(tutor));
    link("/api/guardian-links", "parentId", signIn(parent));
    String lesson = createLesson("Photosynthesis", INITIAL_BODY);
    UUID lessonId = UUID.fromString(lesson);
    for (String reader : List.of(student, tutor, parent)) {
      read(reader, lesson).andExpect(status().isNotFound());
      as(reader, get(courseLessons(course))).andExpect(jsonPath("$.content").isEmpty());
    }
    assertThat(lessonQuery.findPublished(lessonId)).isEmpty();
    action(teacher, lesson, "submit").andExpect(jsonPath("$.status").value("IN_REVIEW"));
    action(admin, lesson, "publish").andExpect(jsonPath("$.publishedVersion").value(1));
    action(teacher, lesson, "revise").andExpect(jsonPath("$.status").value("DRAFT"));
    String newTopic = addTopic(teacher, course, "Cells");
    update(teacher, lesson, newTopic, "Private new title", REVISED_BODY).andExpect(status().isOk());

    read(teacher, lesson)
        .andExpect(jsonPath("$.title").value("Private new title"))
        .andExpect(jsonPath("$.topicId").value(newTopic))
        .andExpect(jsonPath("$.bodyMarkdown").value(REVISED_BODY));
    for (String reader : List.of(student, tutor, parent)) {
      read(reader, lesson)
          .andExpect(jsonPath("$.title").value("Photosynthesis"))
          .andExpect(jsonPath("$.topicId").value(topic))
          .andExpect(jsonPath("$.bodyMarkdown").value(INITIAL_BODY))
          .andExpect(jsonPath("$.version").value(1))
          .andExpect(jsonPath("$.status").doesNotHaveJsonPath());
      as(reader, get(courseLessons(course)).param("status", "PUBLISHED"))
          .andExpect(jsonPath("$.content[0].id").value(lesson))
          .andExpect(jsonPath("$.content[0].topicId").value(topic))
          .andExpect(jsonPath("$.content[0].title").value("Photosynthesis"));
      as(reader, get(courseLessons(course)).param("status", "DRAFT"))
          .andExpect(jsonPath("$.content").isEmpty());
    }
    assertThat(lessonQuery.findPublished(lessonId))
        .hasValueSatisfying(published -> {
          assertThat(published.topicId()).isEqualTo(UUID.fromString(topic));
          assertThat(published.bodyMarkdown()).isEqualTo(INITIAL_BODY);
        });
    assertThat(lessonQuery.findLesson(lessonId))
        .hasValueSatisfying(ref -> {
          assertThat(ref.topicId()).isEqualTo(UUID.fromString(newTopic));
          assertThat(ref.status()).isEqualTo(LessonStatus.DRAFT);
        });

    action(teacher, lesson, "submit").andExpect(status().isOk());
    action(admin, lesson, "publish").andExpect(jsonPath("$.publishedVersion").value(2));
    read(student, lesson).andExpect(jsonPath("$.bodyMarkdown").value(REVISED_BODY));
    as(teacher, get(lessonPath(lesson) + "/versions"))
        .andExpect(jsonPath("$.content[0].version").value(2))
        .andExpect(jsonPath("$.content[1].version").value(1))
        .andExpect(jsonPath("$.content[1].bodyMarkdown").value(INITIAL_BODY));

    LessonPublished first =
        new LessonPublished(lessonId, 1, UUID.fromString(course), UUID.fromString(topic));
    LessonPublished second =
        new LessonPublished(lessonId, 2, UUID.fromString(course), UUID.fromString(newTopic));
    assertThat(events.stream(LessonPublished.class)).containsExactly(first, second);
    assertThat(probe.publications).containsExactly(first, second);

    action(admin, lesson, "archive").andExpect(jsonPath("$.status").value("ARCHIVED"));
    action(admin, lesson, "archive")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(containsString("ARCHIVED")));
    assertThat(events.stream(LessonArchived.class)).containsExactly(new LessonArchived(lessonId));
    assertThat(probe.archives).containsExactly(new LessonArchived(lessonId));
    read(student, lesson).andExpect(status().isNotFound());
    as(student, get(courseLessons(course))).andExpect(jsonPath("$.content").isEmpty());
    as(admin, get(lessonPath(lesson) + "/versions"))
        .andExpect(jsonPath("$.totalElements").value(2));
    as(teacher, delete("/api/topics/" + topic)).andExpect(status().isConflict());
  }

  @Test
  void blocksOutsidersAndKeepsTheReviewCommentUntilResubmit() throws Exception {
    String lesson = createLesson("Draft", INITIAL_BODY);
    String otherCourse = createCourse(otherTeacher);
    String otherTopic = addTopic(otherTeacher, otherCourse, "Other");

    asJson(otherTeacher, post(courseLessons(course)), draft(topic, "Draft", INITIAL_BODY))
        .andExpect(status().isNotFound());
    update(otherTeacher, lesson, topic, "Outsider", REVISED_BODY).andExpect(status().isNotFound());
    action(otherTeacher, lesson, "submit").andExpect(status().isNotFound());
    read(otherTeacher, lesson).andExpect(status().isNotFound());
    as(otherTeacher, get(lessonPath(lesson) + "/versions")).andExpect(status().isNotFound());
    as(student, get(courseLessons(course))).andExpect(status().isNotFound());
    update(teacher, lesson, otherTopic, "Invalid topic", REVISED_BODY)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.topicId").exists());
    as(teacher, delete("/api/topics/" + topic)).andExpect(status().isConflict());

    action(admin, lesson, "publish")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(containsString("DRAFT")));
    update(teacher, lesson, topic, "Edited", INITIAL_BODY).andExpect(status().isOk());
    action(teacher, lesson, "submit").andExpect(status().isOk());
    update(teacher, lesson, topic, "Edited again", REVISED_BODY)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value(containsString("IN_REVIEW")));

    as(admin, get("/api/lessons").param("status", "IN_REVIEW").param("size", "1"))
        .andExpect(jsonPath("$.content[0].id").value(lesson));
    as(teacher, get("/api/lessons").param("status", "IN_REVIEW")).andExpect(status().isForbidden());
    String foreignAdmin = token("foreign-admin", "ADMIN");
    UUID foreignAdminId = signIn(foreignAdmin);
    UUID foreignSchool = UUID.randomUUID();
    jdbc.update(
        "insert into school (id, name, created_at) values (?, ?, now())",
        foreignSchool,
        "Other school");
    try {
      jdbc.update("update app_user set school_id = ? where id = ?", foreignSchool, foreignAdminId);
      as(foreignAdmin, get("/api/lessons").param("status", "IN_REVIEW"))
          .andExpect(jsonPath("$.content").isEmpty());
      action(foreignAdmin, lesson, "publish").andExpect(status().isNotFound());
    } finally {
      jdbc.update("update app_user set school_id = ? where id = ?", SCHOOL, foreignAdminId);
      jdbc.update("delete from school where id = ?", foreignSchool);
    }

    asJson(admin, post(lessonPath(lesson) + "/request-changes"), "{\"comment\":\"Add an example\"}")
        .andExpect(jsonPath("$.status").value("DRAFT"));
    read(teacher, lesson).andExpect(jsonPath("$.reviewComment").value("Add an example"));
    action(teacher, lesson, "submit").andExpect(jsonPath("$.reviewComment").doesNotExist());
  }

  @Test
  void publicationListenersReceiveOnlyCommittedChanges() throws Exception {
    UUID id = UUID.fromString(createLesson("Check commit", INITIAL_BODY));
    action(teacher, id.toString(), "submit").andExpect(status().isOk());
    CurrentUser administrator = new CurrentUser(adminId, "admin", Role.ADMIN, SCHOOL);

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(state -> {
      lessonService.publish(administrator, id);
      assertThat(probe.publications).isEmpty();
      state.setRollbackOnly();
    });
    assertThat(probe.publications).isEmpty();
    assertThat(lessonQuery.findPublished(id)).isEmpty();

    transaction.executeWithoutResult(state -> {
      lessonService.publish(administrator, id);
      assertThat(probe.publications).isEmpty();
    });
    assertThat(probe.publications)
        .singleElement()
        .satisfies(event -> {
          assertThat(event.lessonId()).isEqualTo(id);
          assertThat(event.version()).isEqualTo(1);
        });
    assertThat(lessonQuery.findPublished(id)).isPresent();
  }

  @Test
  void staleConcurrentDraftUpdateReturns409WithoutOverwritingTheWinner() throws Exception {
    String lesson = createLesson("Draft", INITIAL_BODY);
    TransactionTemplate stale = new TransactionTemplate(transactionManager);
    TransactionTemplate winner = new TransactionTemplate(transactionManager);
    winner.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    stale.executeWithoutResult(state -> {
      assertThat(entityManager.find(Lesson.class, UUID.fromString(lesson)).getTitle())
          .isEqualTo("Draft");
      winner.executeWithoutResult(ignored -> assertUpdateStatus(lesson, "Winner", 200));
      assertUpdateStatus(lesson, "Stale", 409);
      state.setRollbackOnly();
    });

    read(teacher, lesson).andExpect(jsonPath("$.title").value("Winner"));
  }

  private void assertUpdateStatus(String lesson, String title, int result) {
    try {
      update(teacher, lesson, topic, title, REVISED_BODY).andExpect(status().is(result));
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  private String token(String name, String role) {
    return TestJwt.bearer(name + "-" + scope, role);
  }

  private UUID signIn(String bearer) throws Exception {
    return UUID.fromString(id(as(bearer, get("/api/me")).andExpect(status().isOk())));
  }

  private String createCourse(String bearer) throws Exception {
    return id(
        asJson(
                bearer,
                post("/api/courses"),
                "{\"title\":\"Biology\",\"subject\":\"Science\",\"gradeLevel\":8}")
            .andExpect(status().isCreated()));
  }

  private String addTopic(String bearer, String courseId, String name) throws Exception {
    return id(
        asJson(bearer, post("/api/courses/" + courseId + "/topics"), "{\"name\":\"" + name + "\"}")
            .andExpect(status().isCreated()));
  }

  private void enroll() throws Exception {
    asJson(
            teacher,
            post("/api/courses/" + course + "/enrollments"),
            "{\"studentId\":\"" + studentId + "\"}")
        .andExpect(status().isCreated());
  }

  private void link(String path, String field, UUID adultId) throws Exception {
    asJson(
            admin,
            post(path),
            """
            {"%s":"%s","studentId":"%s"}
            """
                .formatted(field, adultId, studentId))
        .andExpect(status().isCreated());
  }

  private String createLesson(String title, String body) throws Exception {
    return id(
        asJson(teacher, post(courseLessons(course)), draft(topic, title, body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT")));
  }

  private ResultActions update(
      String bearer, String lesson, String topicId, String title, String body) throws Exception {
    return asJson(bearer, put(lessonPath(lesson)), draft(topicId, title, body));
  }

  private ResultActions action(String bearer, String lesson, String action) throws Exception {
    return as(bearer, post(lessonPath(lesson) + "/" + action));
  }

  private ResultActions read(String bearer, String lesson) throws Exception {
    return as(bearer, get(lessonPath(lesson)));
  }

  private ResultActions as(String bearer, MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer));
  }

  private ResultActions asJson(String bearer, MockHttpServletRequestBuilder request, String body)
      throws Exception {
    return as(bearer, request.contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }

  private static String courseLessons(String courseId) {
    return "/api/courses/" + courseId + "/lessons";
  }

  private static String lessonPath(String id) {
    return "/api/lessons/" + id;
  }

  private static String draft(String topicId, String title, String body) {
    return """
    {"title":"%s","topicId":"%s","bodyMarkdown":"%s"}
    """
        .formatted(title, topicId, body.replace("\n", "\\n"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class EventConfig {
    @Bean
    EventProbe lessonEventProbe() {
      return new EventProbe();
    }
  }

  static class EventProbe {
    final List<LessonPublished> publications = new ArrayList<>();
    final List<LessonArchived> archives = new ArrayList<>();

    @TransactionalEventListener
    void published(LessonPublished event) {
      publications.add(event);
    }

    @TransactionalEventListener
    void archived(LessonArchived event) {
      archives.add(event);
    }
  }
}
