package com.example.file.api.request;

import com.example.core.types.CustomerNo;
import com.example.file.types.FileId;
import com.example.core.types.ProductNo;
import com.example.core.types.UserNo;

import java.time.Duration;

public record ApplyDownloadTokenRequest(
  FileId fileId,
  String sourceApp,
  CustomerNo customerNo,
  ProductNo productNo,
  UserNo downloader,
  Duration ttl
) {
}
