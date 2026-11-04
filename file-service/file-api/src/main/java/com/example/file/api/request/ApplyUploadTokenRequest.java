package com.example.file.api.request;

import com.example.core.types.CustomerNo;
import com.example.core.types.ProductNo;
import com.example.core.types.UserNo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record ApplyUploadTokenRequest(
  String bizType,
  String sourceApp,
  String businessBatchId,
  CustomerNo customerNo,
  ProductNo productNo,
  UserNo uploader,
  LocalDateTime expiresAt,
  List<String> allowedContentTypes,
  Long allowedMaxSize,
  Duration ttl
) {
}
