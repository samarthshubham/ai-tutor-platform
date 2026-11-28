package io.github.samarthshubham.tutor.course.web;

import io.github.samarthshubham.tutor.course.service.TopicResponse;
import io.github.samarthshubham.tutor.course.service.TopicService;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('ADMIN', 'TEACHER')")
public class TopicController {

  public record AddTopicRequest(@NotBlank @Size(max = 200) String name, @Min(1) Integer position) {}

  public record UpdateTopicRequest(
      @NotBlank @Size(max = 200) String name, @NotNull @Min(1) Integer position) {}

  private final TopicService topics;

  public TopicController(TopicService topics) {
    this.topics = topics;
  }

  @PostMapping("/courses/{courseId}/topics")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add a topic; without a position it goes last")
  public TopicResponse add(
      CurrentUser user, @PathVariable UUID courseId, @Valid @RequestBody AddTopicRequest request) {
    return topics.add(user, courseId, request.name(), request.position());
  }

  @PutMapping("/topics/{id}")
  @Operation(summary = "Rename or reorder a topic")
  public TopicResponse update(
      CurrentUser user, @PathVariable UUID id, @Valid @RequestBody UpdateTopicRequest request) {
    return topics.update(user, id, request.name(), request.position());
  }

  @DeleteMapping("/topics/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a topic; 409 when something uses it")
  public void delete(CurrentUser user, @PathVariable UUID id) {
    topics.delete(user, id);
  }
}
