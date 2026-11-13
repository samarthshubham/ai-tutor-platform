package io.github.samarthshubham.tutor.ai.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmbeddingCheckRequest(@NotBlank @Size(max = 20000) String text) {}
