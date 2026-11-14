package io.github.samarthshubham.tutor.identity.web;

import io.github.samarthshubham.tutor.common.api.ErrorResponse;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.service.UserResponse;
import io.github.samarthshubham.tutor.identity.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MeController {

  private final UserService users;

  public MeController(UserService users) {
    this.users = users;
  }

  @GetMapping("/me")
  @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TUTOR', 'STUDENT', 'PARENT')")
  @Operation(summary = "The signed-in user; creates the app_user row on the first call")
  @ApiResponse(responseCode = "200", description = "The current user")
  @ApiResponse(
      responseCode = "401",
      description = "Missing, expired or invalid token",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The token does not carry exactly one of the five roles",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public UserResponse me(CurrentUser user) {
    return users.get(user.id());
  }
}
