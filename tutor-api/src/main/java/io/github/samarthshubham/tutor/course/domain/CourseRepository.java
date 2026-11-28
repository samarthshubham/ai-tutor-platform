package io.github.samarthshubham.tutor.course.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourseRepository
    extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {}
