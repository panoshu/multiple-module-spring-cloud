package com.example.file.api.response;

import java.util.Map;

public record ParsedRowDTO(
    String rowId,
    int rowIndex,
    Map<String, Object> data,
    boolean isValid,
    String errorMessage
) {}
