package io.github.samarthshubham.tutor.identity.config;

import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(security = @SecurityRequirement(name = OpenApiSecurityConfig.BEARER))
@SecurityScheme(
    name = OpenApiSecurityConfig.BEARER,
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiSecurityConfig {

  static final String BEARER = "bearer";

  static {
    SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
  }
}
