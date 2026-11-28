package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.course.api.CourseAccess;
import io.github.samarthshubham.tutor.course.domain.Course;
import io.github.samarthshubham.tutor.course.domain.CourseRepository;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class WritableCourses {

  private final CourseRepository courses;
  private final CourseAccess access;

  WritableCourses(CourseRepository courses, CourseAccess access) {
    this.courses = courses;
    this.access = access;
  }

  Course require(CurrentUser user, UUID courseId) {
    access.requireCourseManagement(user, courseId);
    Course course =
        courses.findById(courseId).orElseThrow(() -> new NotFoundException("Course not found"));
    if (course.isArchived()) {
      throw new ConflictException("Course is archived");
    }
    return course;
  }
}
