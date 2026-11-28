package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.domain.Enrollment;
import io.github.samarthshubham.tutor.course.domain.EnrollmentId;
import io.github.samarthshubham.tutor.course.domain.EnrollmentRepository;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.RelationshipQuery;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrollmentService {

  static final String DUPLICATE_ENROLLMENT = "This student is already enrolled in this course";

  private final EnrollmentRepository enrollments;
  private final CourseAccess access;
  private final WritableCourses writable;
  private final RelationshipQuery relationships;
  private final Clock clock;

  EnrollmentService(
      EnrollmentRepository enrollments,
      CourseAccess access,
      WritableCourses writable,
      RelationshipQuery relationships,
      Clock clock) {
    this.enrollments = enrollments;
    this.access = access;
    this.writable = writable;
    this.relationships = relationships;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<UserSummary> students(CurrentUser user, UUID courseId) {
    access.requireCourseManagement(user, courseId);
    return relationships.findUsers(
        enrollments.findByCourseId(courseId).stream().map(Enrollment::getStudentId).toList());
  }

  @Transactional(readOnly = true)
  public List<UserSummary> searchStudents(CurrentUser user, UUID courseId, String query) {
    writable.require(user, courseId);
    return relationships.searchStudents(user.schoolId(), query.strip());
  }

  @Transactional
  public EnrollmentResponse enroll(CurrentUser user, UUID courseId, UUID studentId) {
    writable.require(user, courseId);
    if (!relationships.hasRoleInSchool(studentId, Role.STUDENT, user.schoolId())) {
      throw new ValidationException(
          "Request is not valid", Map.of("studentId", "must be a STUDENT in your school"));
    }
    if (enrollments.existsById(new EnrollmentId(courseId, studentId))) {
      throw new ConflictException(DUPLICATE_ENROLLMENT);
    }
    try {
      enrollments.saveAndFlush(new Enrollment(courseId, studentId, Instant.now(clock)));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(DUPLICATE_ENROLLMENT);
    }
    return new EnrollmentResponse(courseId, studentId);
  }

  @Transactional
  public void remove(CurrentUser user, UUID courseId, UUID studentId) {
    writable.require(user, courseId);
    EnrollmentId id = new EnrollmentId(courseId, studentId);
    if (!enrollments.existsById(id)) {
      throw new NotFoundException("Enrollment not found");
    }
    enrollments.deleteById(id);
  }
}
