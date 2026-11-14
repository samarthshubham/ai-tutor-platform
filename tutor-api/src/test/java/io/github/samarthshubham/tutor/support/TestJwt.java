package io.github.samarthshubham.tutor.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.github.samarthshubham.tutor.identity.api.Role;
import io.github.samarthshubham.tutor.identity.service.RealmRoleConverter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

public final class TestJwt {

  public static final String ISSUER = "http://localhost:8180/realms/tutor";

  private static final KeyPair SIGNING_KEY = rsaKeyPair();
  private static final KeyPair UNKNOWN_KEY = rsaKeyPair();
  private static final Duration LIFETIME = Duration.ofMinutes(5);

  private TestJwt() {}

  public static JwtRequestPostProcessor as(Role role) {
    return as(defaultUsername(role), role);
  }

  public static JwtRequestPostProcessor as(String username, Role role) {
    return withRealmRoles(username, role.name());
  }

  public static JwtRequestPostProcessor withRealmRoles(String username, String... realmRoles) {
    Map<String, Object> claims = claims(username, realmRoles);
    return jwt()
        .jwt(builder -> claims.forEach(builder::claim))
        .authorities(new RealmRoleConverter());
  }

  public static String bearer(String username, String... realmRoles) {
    Instant now = Instant.now();
    return "Bearer " + sign(SIGNING_KEY, claims(username, realmRoles), now, now.plus(LIFETIME));
  }

  public static String expiredBearer(String username, String... realmRoles) {
    Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
    return "Bearer "
        + sign(SIGNING_KEY, claims(username, realmRoles), issuedAt, issuedAt.plus(LIFETIME));
  }

  public static String bearerSignedWithUnknownKey(String username, String... realmRoles) {
    Instant now = Instant.now();
    return "Bearer " + sign(UNKNOWN_KEY, claims(username, realmRoles), now, now.plus(LIFETIME));
  }

  public static String keycloakId(String username) {
    return UUID.nameUUIDFromBytes(username.getBytes(StandardCharsets.UTF_8)).toString();
  }

  static RSAPublicKey publicKey() {
    return (RSAPublicKey) SIGNING_KEY.getPublic();
  }

  private static String defaultUsername(Role role) {
    return role.name().toLowerCase(Locale.ROOT) + "1";
  }

  private static Map<String, Object> claims(String username, String... realmRoles) {
    Map<String, Object> claims = new LinkedHashMap<>();
    claims.put("iss", ISSUER);
    claims.put("sub", keycloakId(username));
    claims.put("preferred_username", username);
    claims.put("name", "Test " + username);
    claims.put("email", username + "@tutor.local");
    claims.put("realm_access", Map.of("roles", List.of(realmRoles)));
    return claims;
  }

  private static String sign(
      KeyPair key, Map<String, Object> claims, Instant issuedAt, Instant expiresAt) {
    JWTClaimsSet.Builder claimSet =
        new JWTClaimsSet.Builder()
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt));
    claims.forEach(claimSet::claim);
    SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimSet.build());
    try {
      jwt.sign(new RSASSASigner(key.getPrivate()));
    } catch (JOSEException e) {
      throw new IllegalStateException("Could not sign the test token", e);
    }
    return jwt.serialize();
  }

  private static KeyPair rsaKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
