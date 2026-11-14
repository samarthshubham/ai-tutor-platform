package io.github.samarthshubham.tutor.identity.web;

import io.github.samarthshubham.tutor.common.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.json.JsonMapper;

public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  static final String MISSING_TOKEN_MESSAGE = "Authentication required";
  static final String INVALID_TOKEN_MESSAGE = "Invalid or expired token";
  static final String FORBIDDEN_MESSAGE = "Access denied";

  private final JsonMapper jsonMapper;

  public JsonSecurityErrorHandler(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    String message =
        authException instanceof OAuth2AuthenticationException
            ? INVALID_TOKEN_MESSAGE
            : MISSING_TOKEN_MESSAGE;
    write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", message);
  }

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", FORBIDDEN_MESSAGE);
  }

  private void write(HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    jsonMapper.writeValue(
        response.getOutputStream(), new ErrorResponse(status.value(), code, message));
  }
}
