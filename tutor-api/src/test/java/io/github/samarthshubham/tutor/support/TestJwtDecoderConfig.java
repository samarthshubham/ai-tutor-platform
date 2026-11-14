package io.github.samarthshubham.tutor.support;

import io.github.samarthshubham.tutor.identity.config.SecurityProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Profile("test")
@TestConfiguration(proxyBeanMethods = false)
public class TestJwtDecoderConfig {

  @Bean
  JwtDecoder jwtDecoder(SecurityProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestJwt.publicKey()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuerUri()));
    return decoder;
  }
}
