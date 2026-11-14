package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.common.api.ForbiddenException;
import io.github.samarthshubham.tutor.identity.api.CurrentUser;
import io.github.samarthshubham.tutor.identity.api.CurrentUserProvider;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.domain.AppUser;
import java.util.Objects;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class JwtCurrentUserProvider implements CurrentUserProvider {

  static final String USERNAME_CLAIM = "preferred_username";
  static final String NAME_CLAIM = "name";
  static final String EMAIL_CLAIM = "email";

  private final UserProvisioningService provisioning;

  public JwtCurrentUserProvider(UserProvisioningService provisioning) {
    this.provisioning = provisioning;
  }

  @Override
  public CurrentUser currentUser() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token)) {
      throw new IllegalStateException("The request is not authenticated with a JWT");
    }
    Role role =
        RealmRoleConverter.singleRole(token.getAuthorities())
            .orElseThrow(() -> new ForbiddenException("Access denied"));
    AppUser user = provisioning.provision(tokenUser(token.getToken(), role));
    return new CurrentUser(user.getId(), user.getKeycloakId(), user.getRole(), user.getSchoolId());
  }

  private static TokenUser tokenUser(Jwt jwt, Role role) {
    String username =
        Objects.requireNonNullElse(jwt.getClaimAsString(USERNAME_CLAIM), jwt.getSubject());
    String displayName = Objects.requireNonNullElse(jwt.getClaimAsString(NAME_CLAIM), username);
    return new TokenUser(
        jwt.getSubject(), username, displayName, jwt.getClaimAsString(EMAIL_CLAIM), role);
  }
}
