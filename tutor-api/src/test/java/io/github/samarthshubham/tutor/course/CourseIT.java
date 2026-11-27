package io.github.samarthshubham.tutor.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.api.CourseQuery;
import io.github.samarthshubham.tutor.course.api.CourseRef;
import io.github.samarthshubham.tutor.course.api.CourseStatus;
import io.github.samarthshubham.tutor.course.api.TopicRef;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.support.IntegrationTest;
import io.github.samarthshubham.tutor.support.TestJwt;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

class CourseIT extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CourseQuery courseQuery;
  @Autowired CourseAccess courseAccess;
  @Autowired EntityManagerFactory entityManagerFactory;

  private String run;
  private String admin;
  private String teacher;
  private String otherTeacher;
  private String studentToken;
  private UUID studentId;
  private UUID otherStudentId;
  private UUID teacherId;
  private UUID otherTeacherId;
  private String tutor;
  private String parent;
  private UUID tutorId;
  private UUID parentId;

  @BeforeEach
  void signInEveryone() throws Exception {
    run = UUID.randomUUID().toString().substring(0, 8);
    admin = TestJwt.bearer("admin-" + run, "ADMIN");
    teacher = TestJwt.bearer("teacher-" + run, "TEACHER");
    otherTeacher = TestJwt.bearer("teacher2-" + run, "TEACHER");
    studentToken = TestJwt.bearer("student-" + run, "STUDENT");
    tutor = TestJwt.bearer("tutor-" + run, "TUTOR");
    parent = TestJwt.bearer("parent-" + run, "PARENT");
    signIn(admin);
    teacherId = signIn(teacher);
    otherTeacherId = signIn(otherTeacher);
    studentId = signIn(studentToken);
    otherStudentId = signIn(TestJwt.bearer("student2-" + run, "STUDENT"));
    tutorId = signIn(tutor);
    parentId = signIn(parent);
  }

  @Test
  void everyRoleSeesExactlyTheCoursesItIsConnectedTo() throws Exception {
    String mine = createCourse(teacher, "Mine");
    String theirs = createCourse(otherTeacher, "Theirs");
    enroll(teacher, mine, studentId).andExpect(status().isCreated());
    link("/api/tutor-assignments", "tutorId", tutorId, studentId);
    link("/api/guardian-links", "parentId", parentId, studentId);

    assertThat(courseIds(admin)).contains(mine, theirs);
    assertThat(courseIds(teacher)).containsExactly(mine);
    assertThat(courseIds(otherTeacher)).containsExactly(theirs);
    assertThat(courseIds(studentToken)).containsExactly(mine);
    assertThat(courseIds(tutor)).containsExactly(mine);
    assertThat(courseIds(parent)).containsExactly(mine);
    assertThat(courseIds(TestJwt.bearer("student2-" + run, "STUDENT"))).isEmpty();
    assertThat(courseIds(TestJwt.bearer("tutor2-" + run, "TUTOR"))).isEmpty();
    assertThat(courseQuery.visibleCourseIds(currentUser(teacherId, Role.TEACHER)))
        .containsExactly(UUID.fromString(mine));
  }

  @Test
  void aCourseOutsideTheCallersReachIs404() throws Exception {
    String course = createCourse(teacher, "Private");

    for (String outsider :
        List.of(otherTeacher, TestJwt.bearer("student2-" + run, "STUDENT"), tutor, parent)) {
      mvc.perform(as(outsider, get("/api/courses/" + course))).andExpect(status().isNotFound());
    }
    mvc.perform(as(admin, get("/api/courses/" + course)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teacherId").value(teacherId.toString()))
        .andExpect(jsonPath("$.teacherName").value("Test teacher-" + run));
    mvc.perform(as(teacher, get("/api/courses/" + UUID.randomUUID())))
        .andExpect(status().isNotFound());
  }

  @Test
  void aTeacherCannotTouchAnotherTeachersCourse() throws Exception {
    String course = createCourse(teacher, "Locked");
    String topic = addTopic(teacher, course, "Cells", 1);

    mvc.perform(json(as(otherTeacher, put("/api/courses/" + course)), courseBody("X", null)))
        .andExpect(status().isNotFound());
    mvc.perform(as(otherTeacher, post("/api/courses/" + course + "/archive")))
        .andExpect(status().isNotFound());
    mvc.perform(
            json(as(otherTeacher, post("/api/courses/" + course + "/topics")), "{\"name\":\"X\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(
            json(as(otherTeacher, put("/api/topics/" + topic)), "{\"name\":\"X\",\"position\":1}"))
        .andExpect(status().isNotFound());
    mvc.perform(as(otherTeacher, delete("/api/topics/" + topic))).andExpect(status().isNotFound());
    enroll(otherTeacher, course, studentId).andExpect(status().isNotFound());
    mvc.perform(as(otherTeacher, get("/api/courses/" + course + "/students")))
        .andExpect(status().isNotFound());
  }

  @Test
  void creationRulesFollowTheCallersRole() throws Exception {
    mvc.perform(json(as(studentToken, post("/api/courses")), courseBody("Nope", null)))
        .andExpect(status().isForbidden());
    mvc.perform(json(as(admin, post("/api/courses")), courseBody("No teacher", null)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.teacherId").value("is required"));
    mvc.perform(json(as(admin, post("/api/courses")), courseBody("Wrong", studentId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.teacherId").exists());
    mvc.perform(json(as(admin, post("/api/courses")), courseBody("Named", otherTeacherId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.teacherId").value(otherTeacherId.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    mvc.perform(
            json(
                as(teacher, post("/api/courses")),
                "{\"title\":\"\",\"subject\":\"S\",\"gradeLevel\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.title").exists())
        .andExpect(jsonPath("$.fields.gradeLevel").exists());
  }

  @Test
  void topicsAreUniquePerCourseAndComeBackInPositionOrder() throws Exception {
    String course = createCourse(teacher, "Ordered");
    addTopic(teacher, course, "Third", 3);
    String first = addTopic(teacher, course, "First", 1);
    addTopic(teacher, course, "Second", 2);
    mvc.perform(
            json(as(teacher, post("/api/courses/" + course + "/topics")), "{\"name\":\"First\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
    mvc.perform(
            json(as(teacher, post("/api/courses/" + course + "/topics")), "{\"name\":\"Last\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.position").value(4));

    assertThat(topicNames(teacher, course)).containsExactly("First", "Second", "Third", "Last");

    mvc.perform(
            json(as(teacher, put("/api/topics/" + first)), "{\"name\":\"Second\",\"position\":9}"))
        .andExpect(status().isConflict());
    mvc.perform(
            json(as(teacher, put("/api/topics/" + first)), "{\"name\":\"Renamed\",\"position\":9}"))
        .andExpect(status().isOk());
    assertThat(topicNames(teacher, course)).containsExactly("Second", "Third", "Last", "Renamed");
    assertThat(courseQuery.topicsOf(UUID.fromString(course)))
        .extracting(TopicRef::name)
        .containsExactly("Second", "Third", "Last", "Renamed");
    assertThat(courseQuery.findTopic(UUID.fromString(first)))
        .get()
        .extracting(TopicRef::courseId)
        .isEqualTo(UUID.fromString(course));

    mvc.perform(as(teacher, delete("/api/topics/" + first))).andExpect(status().isNoContent());
    mvc.perform(as(teacher, delete("/api/topics/" + first))).andExpect(status().isNotFound());
  }

  @Test
  void enrollmentRulesHold() throws Exception {
    String course = createCourse(teacher, "Enrolling");

    enroll(teacher, course, studentId)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.studentId").value(studentId.toString()));
    enroll(teacher, course, studentId).andExpect(status().isConflict());
    enroll(teacher, course, otherTeacherId)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.studentId").exists());
    enroll(teacher, course, UUID.randomUUID()).andExpect(status().isBadRequest());

    mvc.perform(as(teacher, get("/api/courses/" + course + "/students")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(studentId.toString()));
    UUID courseId = UUID.fromString(course);
    assertThat(courseQuery.isEnrolled(courseId, studentId)).isTrue();
    assertThat(courseQuery.isEnrolled(courseId, otherStudentId)).isFalse();
    assertThat(courseQuery.studentIdsOf(courseId)).containsExactly(studentId);

    mvc.perform(as(teacher, delete("/api/courses/" + course + "/enrollments/" + studentId)))
        .andExpect(status().isNoContent());
    mvc.perform(as(teacher, delete("/api/courses/" + course + "/enrollments/" + studentId)))
        .andExpect(status().isNotFound());
    assertThat(courseQuery.studentIdsOf(courseId)).isEmpty();
  }

  @Test
  void anArchivedCourseStaysReadableAndRejectsEveryWrite() throws Exception {
    String course = createCourse(teacher, "Archived");
    String topic = addTopic(teacher, course, "Cells", 1);
    enroll(teacher, course, studentId).andExpect(status().isCreated());

    mvc.perform(as(teacher, post("/api/courses/" + course + "/archive")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));

    mvc.perform(as(teacher, get("/api/courses/" + course))).andExpect(status().isOk());
    mvc.perform(as(studentToken, get("/api/courses/" + course))).andExpect(status().isOk());
    mvc.perform(json(as(teacher, put("/api/courses/" + course)), courseBody("X", null)))
        .andExpect(status().isConflict());
    mvc.perform(as(teacher, post("/api/courses/" + course + "/archive")))
        .andExpect(status().isConflict());
    mvc.perform(json(as(teacher, post("/api/courses/" + course + "/topics")), "{\"name\":\"New\"}"))
        .andExpect(status().isConflict());
    mvc.perform(json(as(teacher, put("/api/topics/" + topic)), "{\"name\":\"X\",\"position\":1}"))
        .andExpect(status().isConflict());
    mvc.perform(as(teacher, delete("/api/topics/" + topic))).andExpect(status().isConflict());
    enroll(teacher, course, otherStudentId).andExpect(status().isConflict());
    mvc.perform(as(teacher, delete("/api/courses/" + course + "/enrollments/" + studentId)))
        .andExpect(status().isConflict());
    assertThat(courseQuery.findCourse(UUID.fromString(course)))
        .get()
        .extracting(CourseRef::status)
        .isEqualTo(CourseStatus.ARCHIVED);
  }

  @Test
  void aTeacherSeesAStudentOnlyThroughEnrollment() throws Exception {
    String course = createCourse(teacher, "Rosters");
    CurrentUser teacherUser = currentUser(teacherId, Role.TEACHER);
    CurrentUser other = currentUser(otherTeacherId, Role.TEACHER);

    assertThat(courseAccess.canSeeStudent(teacherUser, studentId)).isFalse();
    enroll(teacher, course, studentId).andExpect(status().isCreated());
    assertThat(courseAccess.canSeeStudent(teacherUser, studentId)).isTrue();
    assertThat(courseAccess.canSeeStudent(teacherUser, otherStudentId)).isFalse();
    assertThat(courseAccess.canSeeStudent(other, studentId)).isFalse();
    assertThat(courseAccess.canManageCourse(teacherUser, UUID.fromString(course))).isTrue();
    assertThat(courseAccess.canManageCourse(other, UUID.fromString(course))).isFalse();
    assertThat(
            courseAccess.canSeeCourse(
                currentUser(studentId, Role.STUDENT), UUID.fromString(course)))
        .isTrue();
  }

  @Test
  void theCourseListRunsAConstantNumberOfStatements() throws Exception {
    for (int i = 0; i < 2; i++) {
      String course = createCourse(otherTeacher, "Few " + i);
      addTopic(otherTeacher, course, "Topic", 1);
    }
    for (int i = 0; i < 20; i++) {
      String course = createCourse(teacher, "Many " + i);
      addTopic(teacher, course, "Topic A", 1);
      addTopic(teacher, course, "Topic B", 2);
      enroll(teacher, course, studentId).andExpect(status().isCreated());
    }
    link("/api/tutor-assignments", "tutorId", tutorId, studentId);
    link("/api/guardian-links", "parentId", parentId, studentId);

    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    try {
      for (String token : List.of(teacher, studentToken, tutor, parent)) {
        mvc.perform(as(token, get("/api/courses").param("size", "100"))).andExpect(status().isOk());
      }
      long fewStatements = statementsFor(otherTeacher, statistics);
      long manyStatements = statementsFor(teacher, statistics);
      assertThat(manyStatements).isEqualTo(fewStatements);
      for (String token : List.of(teacher, studentToken, tutor, parent)) {
        assertThat(statementsFor(token, statistics)).isLessThanOrEqualTo(8);
      }
    } finally {
      statistics.setStatisticsEnabled(false);
    }
    mvc.perform(as(teacher, get("/api/courses")))
        .andExpect(jsonPath("$.totalElements").value(20))
        .andExpect(jsonPath("$.content.length()").value(20))
        .andExpect(jsonPath("$.content[0].topics.length()").value(2));
  }

  @Test
  void theDatabaseEnforcesItsConstraints() throws Exception {
    String course = createCourse(teacher, "Constraints");
    UUID courseId = UUID.fromString(course);
    addTopic(teacher, course, "Cells", 1);
    enroll(teacher, course, studentId).andExpect(status().isCreated());

    assertThatThrownBy(() ->
            jdbc.update(
                "insert into topic (id, course_id, name, position, created_at)"
                    + " values (?, ?, 'Cells', 2, now())",
                UUID.randomUUID(),
                courseId))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update(
                "insert into enrollment (course_id, student_id, created_at)"
                    + " values (?, ?, now())",
                courseId,
                studentId))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update course set grade_level = 13 where id = ?", courseId))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update course set status = 'GONE' where id = ?", courseId))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() ->
            jdbc.update(
                "insert into enrollment (course_id, student_id, created_at)"
                    + " values (?, ?, now())",
                UUID.randomUUID(),
                studentId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private long statementsFor(String token, Statistics statistics) throws Exception {
    statistics.clear();
    mvc.perform(as(token, get("/api/courses").param("size", "100"))).andExpect(status().isOk());
    return statistics.getPrepareStatementCount();
  }

  private List<String> topicNames(String token, String course) throws Exception {
    String body =
        mvc.perform(as(token, get("/api/courses/" + course)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.topics[*].name");
  }

  private String createCourse(String token, String title) throws Exception {
    String body =
        mvc.perform(json(as(token, post("/api/courses")), courseBody(title, null)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private String addTopic(String token, String course, String name, int position) throws Exception {
    String body =
        mvc.perform(
                json(
                    as(token, post("/api/courses/" + course + "/topics")),
                    "{\"name\":\"%s\",\"position\":%d}".formatted(name, position)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.id");
  }

  private ResultActions enroll(String token, String course, UUID student) throws Exception {
    return mvc.perform(
        json(
            as(token, post("/api/courses/" + course + "/enrollments")),
            "{\"studentId\":\"%s\"}".formatted(student)));
  }

  private void link(String path, String field, UUID other, UUID student) throws Exception {
    mvc.perform(
            json(
                as(admin, post(path)),
                "{\"%s\":\"%s\",\"studentId\":\"%s\"}".formatted(field, other, student)))
        .andExpect(status().isCreated());
  }

  private List<String> courseIds(String token) throws Exception {
    String body =
        mvc.perform(as(token, get("/api/courses").param("size", "100")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.content[*].id");
  }

  private String courseBody(String title, UUID teacherId) {
    String teacher = teacherId == null ? "null" : "\"" + teacherId + "\"";
    return "{\"title\":\"%s-%s\",\"subject\":\"Science\",\"gradeLevel\":8,\"teacherId\":%s}"
        .formatted(title, run, teacher);
  }

  private CurrentUser currentUser(UUID id, Role role) {
    UUID school =
        jdbc.queryForObject("select school_id from app_user where id = ?", UUID.class, id);
    return new CurrentUser(id, "kc-" + id, role, school);
  }

  private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder request) {
    return request.header(HttpHeaders.AUTHORIZATION, token);
  }

  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
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
