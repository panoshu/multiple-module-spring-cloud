package com.example.file.api.request;

import jakarta.validation.constraints.NotBlank;

public record FetchRowsRequest(
    @NotBlank String subTaskId,
    int page,
    int size
) {
  public FetchRowsRequest {
    if (page <= 0) page = 1;
    if (size <= 0) size = 20;
    if (size > 500) size = 500;
  }
}
