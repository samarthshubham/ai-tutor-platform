package io.github.samarthshubham.tutor.ai.web;

public record PingResponse(String provider, String model, String reply, long latencyMs) {}
