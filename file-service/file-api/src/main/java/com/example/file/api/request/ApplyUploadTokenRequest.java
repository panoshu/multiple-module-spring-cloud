package com.example.file.api.request;

import com.example.core.types.CustomerNo;
import com.example.core.types.ProductNo;
import com.example.core.types.UserNo;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

public record ApplyUploadTokenRequest(
  String bizType,
  /**
   * 文件品类（批次3 A1：FORM 需解析路由预判 / MATERIAL 直存；空则按 MATERIAL 语义兼容）
   */
  String category,
  /**
   * 业务上下文（批次3 A3：FORM 品类的模板路由维度——productNo/planNo/accountManager 等）
   */
  java.util.Map<String, String> routingContext,
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
