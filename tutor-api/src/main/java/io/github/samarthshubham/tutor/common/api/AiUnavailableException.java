package io.github.samarthshubham.tutor.common.api;

public class AiUnavailableException extends RuntimeException {

  public AiUnavailableException(String message) {
    super(message);
  }

  public AiUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
