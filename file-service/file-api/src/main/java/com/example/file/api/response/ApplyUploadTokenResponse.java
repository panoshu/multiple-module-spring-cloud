package com.example.file.api.response;

import com.example.shared.identifier.id.FileId;

public record ApplyUploadTokenResponse(String token, FileId fileId) {
}
