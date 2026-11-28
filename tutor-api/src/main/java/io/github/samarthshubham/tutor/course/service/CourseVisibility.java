package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.course.domain.Course;
import io.github.samarthshubham.tutor.course.domain.Enrollment;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.RelationshipQuery;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
class CourseVisibility {

  private final RelationshipQuery relationships;

  CourseVisibility(RelationshipQuery relationships) {
    this.relationships = relationships;
  }

  Specification<Course> visibleTo(CurrentUser user) {
    Specification<Course> inSchool =
        (root, query, cb) -> cb.equal(root.get("schoolId"), user.schoolId());
    return switch (user.role()) {
      case ADMIN -> inSchool;
      case TEACHER -> inSchool.and((root, query, cb) -> cb.equal(root.get("teacherId"), user.id()));
      case STUDENT -> inSchool.and(enrolled(Set.of(user.id())));
      case TUTOR -> inSchool.and(enrolled(relationships.studentIdsOfTutor(user.id())));
      case PARENT -> inSchool.and(enrolled(relationships.studentIdsOfParent(user.id())));
    };
  }

  Specification<Course> withId(UUID courseId) {
    return (root, query, cb) -> cb.equal(root.get("id"), courseId);
  }

  private static Specification<Course> enrolled(Set<UUID> studentIds) {
    if (studentIds.isEmpty()) {
      return (root, query, cb) -> cb.disjunction();
    }
    return (root, query, cb) -> {
      var enrollments = query.subquery(UUID.class);
      var enrollment = enrollments.from(Enrollment.class);
      enrollments
          .select(enrollment.get("courseId"))
          .where(enrollment.get("studentId").in(studentIds));
      return root.get("id").in(enrollments);
    };
  }
}
