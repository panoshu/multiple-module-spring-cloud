package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record GetTemplateConfigRequest(
  @NotBlank String bizType,
  String templateCode,
  String version
) {
}
