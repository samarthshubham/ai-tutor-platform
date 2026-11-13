package io.github.samarthshubham.tutor.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

public record ErrorResponse(
    int status,
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fields) {

  public ErrorResponse(int status, String code, String message) {
    this(status, code, message, null);
  }
}
