package com.example.file.api.response;

import com.example.shared.primitives.identity.FileId;

public record ApplyUploadTokenResponse(String token, FileId fileId) {}
