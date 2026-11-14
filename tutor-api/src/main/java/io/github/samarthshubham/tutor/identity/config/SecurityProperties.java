package io.github.samarthshubham.tutor.identity.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("tutor.security")
public record SecurityProperties(
    @NotBlank String issuerUri, @NotEmpty List<String> allowedOrigins) {}
