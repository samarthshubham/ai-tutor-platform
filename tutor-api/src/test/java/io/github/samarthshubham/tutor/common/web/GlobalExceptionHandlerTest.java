package io.github.samarthshubham.tutor.common.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.samarthshubham.tutor.common.api.AiUnavailableException;
import io.github.samarthshubham.tutor.common.api.ConflictException;
import io.github.samarthshubham.tutor.common.api.ForbiddenException;
import io.github.samarthshubham.tutor.common.api.NotFoundException;
import io.github.samarthshubham.tutor.common.api.RateLimitedException;
import io.github.samarthshubham.tutor.common.api.ValidationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  record CreateRequest(@NotBlank String title) {}

  @RestController
  static class ThrowingController {

    @GetMapping("/not-found")
    void notFound() {
      throw new NotFoundException("Course not found");
    }

    @GetMapping("/conflict")
    void conflict() {
      throw new ConflictException("Lesson is already published");
    }

    @GetMapping("/forbidden")
    void forbidden() {
      throw new ForbiddenException("Role may not do this");
    }

    @GetMapping("/access-denied")
    void accessDenied() {
      throw new AuthorizationDeniedException("Access Denied");
    }

    @GetMapping("/ai-unavailable")
    void aiUnavailable() {
      throw new AiUnavailableException("Model timed out");
    }

    @GetMapping("/rate-limited")
    void rateLimited() {
      throw new RateLimitedException("Too many AI requests", Duration.ofSeconds(42));
    }

    @GetMapping("/business-validation")
    void businessValidation() {
      throw new ValidationException("Request is not valid", Map.of("parentId", "must be a parent"));
    }

    @GetMapping("/business-validation-without-fields")
    void businessValidationWithoutFields() {
      throw new ValidationException("Session has no questions");
    }

    @GetMapping("/constraint-violation")
    void constraintViolation() {
      var violations =
          Validation.buildDefaultValidatorFactory().getValidator().validate(new CreateRequest(""));
      throw new ConstraintViolationException(violations);
    }

    @PostMapping("/create")
    void create(@Valid @RequestBody CreateRequest request) {}

    @GetMapping("/items")
    void items(@RequestParam @Max(100) int size) {}

    @GetMapping("/items/{id}")
    void item(@PathVariable java.util.UUID id) {}

    @GetMapping("/boom")
    void boom() {
      throw new IllegalStateException("secret internal detail");
    }
  }

  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ThrowingController())
          .setControllerAdvice(new GlobalExceptionHandler())
          .build();

  @Test
  void notFoundException() throws Exception {
    mvc.perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Course not found"))
        .andExpect(jsonPath("$.fields").doesNotExist());
  }

  @Test
  void unknownPath() throws Exception {
    mvc.perform(get("/no-such-path"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("Resource not found"));
  }

  @Test
  void conflictException() throws Exception {
    mvc.perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"))
        .andExpect(jsonPath("$.message").value("Lesson is already published"));
  }

  @Test
  void forbiddenException() throws Exception {
    mvc.perform(get("/forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void deniedMethodSecurityCheckIsForbidden() throws Exception {
    mvc.perform(get("/access-denied"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value("FORBIDDEN"))
        .andExpect(jsonPath("$.message").value("Access denied"));
  }

  @Test
  void aiUnavailableException() throws Exception {
    mvc.perform(get("/ai-unavailable"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value("Model timed out"));
  }

  @Test
  void rateLimitedExceptionSetsRetryAfter() throws Exception {
    mvc.perform(get("/rate-limited"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "42"))
        .andExpect(jsonPath("$.status").value(429))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void businessValidationWithFields() throws Exception {
    mvc.perform(get("/business-validation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.parentId").value("must be a parent"));
  }

  @Test
  void businessValidationWithoutFields() throws Exception {
    mvc.perform(get("/business-validation-without-fields"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Session has no questions"))
        .andExpect(jsonPath("$.fields").doesNotExist());
  }

  @Test
  void invalidBodyListsFields() throws Exception {
    mvc.perform(post("/create").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request is not valid"))
        .andExpect(jsonPath("$.fields.title").value("must not be blank"));
  }

  @Test
  void invalidParameterListsFields() throws Exception {
    mvc.perform(get("/items").param("size", "500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.size").exists());
  }

  @Test
  void constraintViolationListsFields() throws Exception {
    mvc.perform(get("/constraint-violation"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.title").value("must not be blank"));
  }

  @Test
  void typeMismatchNamesTheParameter() throws Exception {
    mvc.perform(get("/items/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields.id").value("has an invalid value"));
  }

  @Test
  void malformedJson() throws Exception {
    mvc.perform(post("/create").contentType(MediaType.APPLICATION_JSON).content("{\"title\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request body is malformed"));
  }

  @Test
  void missingParameterIsValidationFailure() throws Exception {
    mvc.perform(get("/items"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void wrongMethodKeepsItsStatus() throws Exception {
    mvc.perform(post("/not-found"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void unexpectedExceptionHidesDetails() throws Exception {
    mvc.perform(get("/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.message").value("Unexpected error"))
        .andExpect(content().string(not(containsString("secret"))))
        .andExpect(content().string(not(containsString("IllegalStateException"))));
  }
}
