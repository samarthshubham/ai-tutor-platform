package io.github.samarthshubham.tutor.ai.web;

import io.github.samarthshubham.tutor.common.api.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiOpsController {

  private final AiOpsService service;

  public AiOpsController(AiOpsService service) {
    this.service = service;
  }

  @GetMapping("/ping")
  @Operation(summary = "Make one tiny model call to prove the provider works")
  @ApiResponse(responseCode = "200", description = "The model replied")
  @ApiResponse(
      responseCode = "503",
      description = "The model timed out or could not be reached",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public PingResponse ping() {
    return service.ping();
  }

  @PostMapping("/embedding")
  @Operation(summary = "Embed a text to prove the embedding model works")
  @ApiResponse(responseCode = "200", description = "The text was embedded")
  @ApiResponse(
      responseCode = "400",
      description = "The text is blank or too long",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "The model timed out or could not be reached",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  public EmbeddingCheckResponse embedding(@Valid @RequestBody EmbeddingCheckRequest request) {
    return service.embed(request.text());
  }
}
