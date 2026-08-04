package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record GetFileTaskRequest(
  @NotBlank String fileTaskId
) {
}
