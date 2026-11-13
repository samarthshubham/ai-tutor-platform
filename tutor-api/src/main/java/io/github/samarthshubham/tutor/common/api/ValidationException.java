package io.github.samarthshubham.tutor.common.api;

import java.util.Map;

public class ValidationException extends RuntimeException {

  private final Map<String, String> fields;

  public ValidationException(String message) {
    super(message);
    this.fields = null;
  }

  public ValidationException(String message, Map<String, String> fields) {
    super(message);
    this.fields = Map.copyOf(fields);
  }

  public Map<String, String> fields() {
    return fields;
  }
}
