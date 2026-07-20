package com.example.file.api.response;

import com.example.shared.primitives.identity.FileId;

public record UploadFileResponse(FileId fileId, String originalName, long size, String digest) {}
