package io.github.samarthshubham.tutor.cms.web;

import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import io.github.samarthshubham.tutor.cms.service.LessonService;
import io.github.samarthshubham.tutor.cms.service.LessonView;
import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
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
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TUTOR', 'STUDENT', 'PARENT')")
public class LessonController {

  public record LessonRequest(
      @NotBlank @Size(max = 200) String title,
      @NotNull UUID topicId,
      @NotNull @Size(min = 1, max = 50_000) String bodyMarkdown) {}

  public record RequestChangesRequest(@NotBlank @Size(max = 2000) String comment) {}

  private final LessonService lessons;

  public LessonController(LessonService lessons) {
    this.lessons = lessons;
  }

  @GetMapping("/courses/{courseId}/lessons")
  @Operation(summary = "List course lessons using the caller's lesson view")
  public PageResponse<? extends LessonView> list(
      CurrentUser user,
      @PathVariable UUID courseId,
      @RequestParam(required = false) LessonStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return lessons.list(user, courseId, status, page, size);
  }

  @PostMapping("/courses/{courseId}/lessons")
  @PreAuthorize("hasRole('TEACHER')")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a draft lesson")
  public LessonView.Staff create(
      CurrentUser user, @PathVariable UUID courseId, @Valid @RequestBody LessonRequest request) {
    return lessons.create(
        user, courseId, request.topicId(), request.title(), request.bodyMarkdown());
  }

  @GetMapping("/lessons/{id}")
  @Operation(summary = "Read a lesson using the caller's lesson view")
  public LessonView get(CurrentUser user, @PathVariable UUID id) {
    return lessons.get(user, id);
  }

  @PutMapping("/lessons/{id}")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Edit the working draft")
  public LessonView.Staff edit(
      CurrentUser user, @PathVariable UUID id, @Valid @RequestBody LessonRequest request) {
    return lessons.edit(user, id, request.topicId(), request.title(), request.bodyMarkdown());
  }

  @PostMapping("/lessons/{id}/submit")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Submit a draft lesson for review")
  public LessonView.Staff submit(CurrentUser user, @PathVariable UUID id) {
    return lessons.submit(user, id);
  }

  @PostMapping("/lessons/{id}/publish")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Publish a version of the reviewed lesson")
  public LessonView.Staff publish(CurrentUser user, @PathVariable UUID id) {
    return lessons.publish(user, id);
  }

  @PostMapping("/lessons/{id}/request-changes")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "Return a lesson to draft with a review comment")
  public LessonView.Staff requestChanges(
      CurrentUser user, @PathVariable UUID id, @Valid @RequestBody RequestChangesRequest request) {
    return lessons.requestChanges(user, id, request.comment());
  }

  @PostMapping("/lessons/{id}/revise")
  @PreAuthorize("hasRole('TEACHER')")
  @Operation(summary = "Reopen a published lesson for editing")
  public LessonView.Staff revise(CurrentUser user, @PathVariable UUID id) {
    return lessons.revise(user, id);
  }

  @PostMapping("/lessons/{id}/archive")
  @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
  @Operation(summary = "Archive a lesson")
  public LessonView.Staff archive(CurrentUser user, @PathVariable UUID id) {
    return lessons.archive(user, id);
  }

  @GetMapping("/lessons/{id}/versions")
  @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
  @Operation(summary = "List published versions, newest first")
  public PageResponse<LessonView.Published> versions(
      CurrentUser user,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return lessons.versions(user, id, page, size);
  }

  @GetMapping("/lessons")
  @PreAuthorize("hasRole('ADMIN')")
  @Operation(summary = "List lessons awaiting review in the administrator's school")
  public PageResponse<LessonView.Staff> reviewQueue(
      CurrentUser user,
      @RequestParam LessonStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    if (status != LessonStatus.IN_REVIEW) {
      throw new ValidationException("Request is not valid", Map.of("status", "Must be IN_REVIEW"));
    }
    return lessons.reviewQueue(user, page, size);
  }
}
