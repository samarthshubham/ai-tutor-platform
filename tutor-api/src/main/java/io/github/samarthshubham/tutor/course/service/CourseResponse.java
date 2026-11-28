package io.github.samarthshubham.tutor.course.service;

import io.github.samarthshubham.tutor.course.api.CourseStatus;
import java.util.List;
import java.util.UUID;

public record CourseResponse(
    UUID id,
    String title,
    String subject,
    int gradeLevel,
    String description,
    UUID teacherId,
    String teacherName,
    CourseStatus status,
    List<TopicResponse> topics) {}
