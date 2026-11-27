package io.github.samarthshubham.tutor.course.web;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.course.service.CourseResponse;
import io.github.samarthshubham.tutor.course.service.CourseService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TUTOR', 'STUDENT', 'PARENT')")
public class CourseController {

  public record CreateCourseRequest(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 100) String subject,
      @Min(1) @Max(12) int gradeLevel,
      @Size(max = 2000) String description,
      UUID teacherId) {}

  public record UpdateCourseRequest(
      @NotBlank @Size(max = 200) String title,
      @NotBlank @Size(max = 100) String subject,
      @Min(1) @Max(12) int gradeLevel,
      @Size(max = 2000) String description) {}

  private final CourseService courses;

  public CourseController(CourseService courses) {
    this.courses = courses;
  }

  @GetMapping
  @Operation(summary = "Page of the courses the caller may see")
  public PageResponse<CourseResponse> list(
      CurrentUser user,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return courses.list(user, page, size);
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a course; a teacher becomes its teacher, an admin names one")
  public CourseResponse create(CurrentUser user, @Valid @RequestBody CreateCourseRequest request) {
    return courses.create(
        user,
        request.title(),
        request.subject(),
        request.gradeLevel(),
        request.description(),
        request.teacherId());
  }

  @GetMapping("/{id}")
  @Operation(summary = "A course with its topics")
  public CourseResponse get(CurrentUser user, @PathVariable UUID id) {
    return courses.get(user, id);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
  @Operation(summary = "Update a course")
  public CourseResponse update(
      CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
    return courses.update(
        user, id, request.title(), request.subject(), request.gradeLevel(), request.description());
  }

  @PostMapping("/{id}/archive")
  @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
  @Operation(summary = "Archive a course")
  public CourseResponse archive(CurrentUser user, @PathVariable UUID id) {
    return courses.archive(user, id);
  }
}
