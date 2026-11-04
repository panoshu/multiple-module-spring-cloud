package com.example.file.api.response;

import com.example.file.types.FileId;

public record ApplyUploadTokenResponse(String token, FileId fileId) {
}
