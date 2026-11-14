package io.github.samarthshubham.tutor.identity.service;

import io.github.samarthshubham.tutor.identity.api.Role;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class RealmRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

  static final String REALM_ACCESS_CLAIM = "realm_access";
  static final String ROLES = "roles";
  static final String ROLE_PREFIX = "ROLE_";

  private static final Set<String> ROLE_NAMES =
      Arrays.stream(Role.values()).map(Role::name).collect(Collectors.toUnmodifiableSet());

  @Override
  public Collection<GrantedAuthority> convert(Jwt jwt) {
    Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
    if (realmAccess == null || !(realmAccess.get(ROLES) instanceof Collection<?> roles)) {
      return List.of();
    }
    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(ROLE_NAMES::contains)
        .distinct()
        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
        .toList();
  }

  public static Optional<Role> singleRole(Collection<? extends GrantedAuthority> authorities) {
    List<Role> roles =
        authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .filter(authority -> authority != null && authority.startsWith(ROLE_PREFIX))
            .map(authority -> authority.substring(ROLE_PREFIX.length()))
            .filter(ROLE_NAMES::contains)
            .distinct()
            .map(Role::valueOf)
            .toList();
    return roles.size() == 1 ? Optional.of(roles.getFirst()) : Optional.empty();
  }
}
