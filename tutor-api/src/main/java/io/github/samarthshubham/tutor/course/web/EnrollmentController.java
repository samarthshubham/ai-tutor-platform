package io.github.samarthshubham.tutor.course.web;

import io.github.samarthshubham.tutor.course.service.EnrollmentResponse;
import io.github.samarthshubham.tutor.course.service.EnrollmentService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.UserSummary;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses/{courseId}")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class EnrollmentController {

  public record EnrollRequest(@NotNull UUID studentId) {}

  private final EnrollmentService enrollments;

  public EnrollmentController(EnrollmentService enrollments) {
    this.enrollments = enrollments;
  }

  @GetMapping("/students")
  @Operation(summary = "The students enrolled in a course")
  public List<UserSummary> students(CurrentUser user, @PathVariable UUID courseId) {
    return enrollments.students(user, courseId);
  }

  @GetMapping("/student-search")
  @Operation(summary = "Find students in the school by name or username for enrollment")
  public List<UserSummary> searchStudents(
      CurrentUser user,
      @PathVariable UUID courseId,
      @RequestParam("q") @NotBlank @Size(max = 100) String query) {
    return enrollments.searchStudents(user, courseId, query);
  }

  @PostMapping("/enrollments")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Enroll a student")
  public EnrollmentResponse enroll(
      CurrentUser user, @PathVariable UUID courseId, @Valid @RequestBody EnrollRequest request) {
    return enrollments.enroll(user, courseId, request.studentId());
  }

  @DeleteMapping("/enrollments/{studentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove an enrollment")
  public void remove(CurrentUser user, @PathVariable UUID courseId, @PathVariable UUID studentId) {
    enrollments.remove(user, courseId, studentId);
  }
}
