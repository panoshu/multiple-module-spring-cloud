package com.example.core.api.form.response;

import java.time.LocalDateTime;

/**
 * 上传 token 响应
 *
 * @author panoshu
 */
public record UploadTokenResponse(
  String token,
  LocalDateTime expireTime,
  String uploadUrl
) {
}
