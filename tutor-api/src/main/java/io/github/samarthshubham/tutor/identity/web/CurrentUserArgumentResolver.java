package io.github.samarthshubham.tutor.identity.web;

import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

  private final CurrentUserProvider currentUserProvider;

  public CurrentUserArgumentResolver(CurrentUserProvider currentUserProvider) {
    this.currentUserProvider = currentUserProvider;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return CurrentUser.class.equals(parameter.getParameterType());
  }

  @Override
  public CurrentUser resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    return currentUserProvider.currentUser();
  }
}
