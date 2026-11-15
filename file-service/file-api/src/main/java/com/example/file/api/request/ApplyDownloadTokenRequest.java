package com.example.file.api.request;

import com.example.file.types.FileId;

import java.time.Duration;

public record ApplyDownloadTokenRequest(
  FileId fileId,
  String sourceApp,
  String customerNo,
  String productNo,
  String downloader,
  Duration ttl
) {
}
