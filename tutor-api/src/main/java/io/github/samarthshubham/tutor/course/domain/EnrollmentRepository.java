package io.github.samarthshubham.tutor.course.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnrollmentRepository extends JpaRepository<Enrollment, EnrollmentId> {

  List<Enrollment> findByCourseId(UUID courseId);

  boolean existsByCourseIdAndStudentIdIn(UUID courseId, Collection<UUID> studentIds);

  @Query(
      "select count(e) > 0 from Enrollment e, Course c"
          + " where e.courseId = c.id and c.teacherId = :teacherId and e.studentId = :studentId")
  boolean isStudentOfTeacher(UUID teacherId, UUID studentId);
}
