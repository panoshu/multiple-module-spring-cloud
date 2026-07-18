package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record CancelFileTaskRequest(
    @NotBlank String fileTaskId,
    @NotBlank String operator
) {}
