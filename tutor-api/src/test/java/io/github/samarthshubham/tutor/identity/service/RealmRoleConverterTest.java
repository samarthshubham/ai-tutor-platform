package io.github.samarthshubham.tutor.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.tutor.identity.api.Role;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RealmRoleConverterTest {

  private final RealmRoleConverter converter = new RealmRoleConverter();

  @Test
  void matchingRealmRolesBecomeRoleAuthorities() {
    assertThat(names(converter.convert(jwtWithRealmRoles(List.of("TEACHER")))))
        .containsExactly("ROLE_TEACHER");
  }

  @Test
  void otherRealmRolesAreIgnored() {
    Jwt jwt =
        jwtWithRealmRoles(
            List.of("default-roles-tutor", "offline_access", "uma_authorization", "student"));

    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void everyMatchingRoleIsMappedOnce() {
    Jwt jwt = jwtWithRealmRoles(List.of("ADMIN", "PARENT", "ADMIN", "default-roles-tutor"));

    assertThat(names(converter.convert(jwt))).containsExactly("ROLE_ADMIN", "ROLE_PARENT");
  }

  @Test
  void missingRealmAccessGivesNoAuthorities() {
    Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").claim("sub", "u").build();

    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void realmAccessWithoutARoleListGivesNoAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("t")
            .header("alg", "none")
            .claim("realm_access", Map.of("roles", "ADMIN"))
            .build();

    assertThat(converter.convert(jwt)).isEmpty();
  }

  @Test
  void nonStringRoleEntriesAreIgnored() {
    assertThat(names(converter.convert(jwtWithRealmRoles(List.of(42, "TUTOR")))))
        .containsExactly("ROLE_TUTOR");
  }

  @Test
  void singleRoleIsFoundAmongOtherAuthorities() {
    List<GrantedAuthority> authorities =
        List.of(
            new SimpleGrantedAuthority("FACTOR_BEARER"),
            new SimpleGrantedAuthority("SCOPE_profile"),
            new SimpleGrantedAuthority("ROLE_STUDENT"),
            new SimpleGrantedAuthority("ROLE_SOMETHING_ELSE"));

    assertThat(RealmRoleConverter.singleRole(authorities)).contains(Role.STUDENT);
  }

  @Test
  void noRoleOrTwoRolesHaveNoSingleRole() {
    assertThat(RealmRoleConverter.singleRole(List.of())).isEmpty();
    assertThat(
            RealmRoleConverter.singleRole(
                List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_TEACHER"))))
        .isEmpty();
  }

  private static Jwt jwtWithRealmRoles(List<?> roles) {
    return Jwt.withTokenValue("t")
        .header("alg", "none")
        .claim("sub", "u")
        .claim("realm_access", Map.of("roles", roles))
        .build();
  }

  private static List<String> names(Collection<GrantedAuthority> authorities) {
    return authorities.stream().map(GrantedAuthority::getAuthority).toList();
  }
}
