package io.github.samarthshubham.tutor.identity.web;

import io.github.samarthshubham.tutor.common.api.PageResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.service.GuardianLinkResponse;
import io.github.samarthshubham.tutor.identity.service.RelationshipService;
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
@RequestMapping("/api/guardian-links")
@PreAuthorize("hasRole('ADMIN')")
public class GuardianLinkController {

  public record CreateGuardianLinkRequest(@NotNull UUID parentId, @NotNull UUID studentId) {}

  private final RelationshipService relationships;

  public GuardianLinkController(RelationshipService relationships) {
    this.relationships = relationships;
  }

  @GetMapping
  @Operation(summary = "Page of guardian links, filtered by student or parent")
  public PageResponse<GuardianLinkResponse> list(
      CurrentUser user,
      @RequestParam(required = false) UUID studentId,
      @RequestParam(required = false) UUID parentId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return relationships.listGuardianLinks(user.schoolId(), parentId, studentId, page, size);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Link a parent to a student")
  public GuardianLinkResponse create(
      CurrentUser user, @Valid @RequestBody CreateGuardianLinkRequest request) {
    return relationships.createGuardianLink(
        request.parentId(), request.studentId(), user.schoolId());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove a guardian link")
  public void delete(CurrentUser user, @PathVariable UUID id) {
    relationships.deleteGuardianLink(id, user.schoolId());
  }
}
