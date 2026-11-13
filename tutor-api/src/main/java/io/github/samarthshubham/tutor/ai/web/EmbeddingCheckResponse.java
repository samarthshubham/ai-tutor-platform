package io.github.samarthshubham.tutor.ai.web;

public record EmbeddingCheckResponse(
    String provider, String model, int dimensions, long latencyMs) {}
