package com.example.file.api.request;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;

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
