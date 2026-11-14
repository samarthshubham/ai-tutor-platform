package io.github.samarthshubham.tutor.identity.web;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
import io.github.samarthshubham.tutor.identity.service.TutorAssignmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/tutor-assignments")
@PreAuthorize("hasRole('ADMIN')")
public class TutorAssignmentController {

  public record CreateTutorAssignmentRequest(@NotNull UUID tutorId, @NotNull UUID studentId) {}

  private final RelationshipService relationships;

  public TutorAssignmentController(RelationshipService relationships) {
    this.relationships = relationships;
  }

  @GetMapping
  @Operation(summary = "Page of tutor assignments, filtered by tutor or student")
  public PageResponse<TutorAssignmentResponse> list(
      CurrentUser user,
      @RequestParam(required = false) UUID tutorId,
      @RequestParam(required = false) UUID studentId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return relationships.listTutorAssignments(user.schoolId(), tutorId, studentId, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Assign a tutor to a student")
  public TutorAssignmentResponse create(
      CurrentUser user, @Valid @RequestBody CreateTutorAssignmentRequest request) {
    return relationships.createTutorAssignment(
        request.tutorId(), request.studentId(), user.schoolId());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a tutor assignment")
  public void delete(CurrentUser user, @PathVariable UUID id) {
    relationships.deleteTutorAssignment(id, user.schoolId());
  }
}
