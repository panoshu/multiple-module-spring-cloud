package com.example.file.api.request;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;

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
