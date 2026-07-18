package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record ActivateTemplateConfigRequest(
    @NotBlank String configId,
    @NotBlank String operator
) {}
