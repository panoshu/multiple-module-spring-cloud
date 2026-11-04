package com.example.file.api.response;

import com.example.file.types.FileId;

public record UploadFileResponse(FileId fileId, String originalName, long size, String digest) {
}
