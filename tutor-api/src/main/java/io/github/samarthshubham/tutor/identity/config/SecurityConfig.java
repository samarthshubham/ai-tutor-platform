package io.github.samarthshubham.tutor.identity.config;

import static org.springframework.security.config.Customizer.withDefaults;

import io.github.samarthshubham.tutor.identity.service.RealmRoleConverter;
import io.github.samarthshubham.tutor.identity.web.JsonSecurityErrorHandler;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

  static final String[] OPEN_PATHS = {
    "/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**"
  };

  @Bean
  SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, JsonMapper jsonMapper) {
    JsonSecurityErrorHandler errorHandler = new JsonSecurityErrorHandler(jsonMapper);
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(withDefaults())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(requests ->
            requests.requestMatchers(OPEN_PATHS).permitAll().anyRequest().access(exactlyOneRole()))
        .oauth2ResourceServer(resourceServer ->
            resourceServer
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(errorHandler)
                .accessDeniedHandler(errorHandler))
        .exceptionHandling(exceptions ->
            exceptions.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler));
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.allowedOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
    cors.setExposedHeaders(List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.LOCATION));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  private static JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(new RealmRoleConverter());
    return converter;
  }

  private static AuthorizationManager<RequestAuthorizationContext> exactlyOneRole() {
    return (authentication, context) ->
        new AuthorizationDecision(
            RealmRoleConverter.singleRole(authentication.get().getAuthorities()).isPresent());
  }
}
