package io.github.samarthshubham.tutor.ai.api;

public class InvalidModelOutputException extends RuntimeException {

  public InvalidModelOutputException(String message) {
    super(message);
  }
}
