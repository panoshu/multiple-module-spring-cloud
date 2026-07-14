package com.example.shared.file.types.dto;

import java.time.OffsetDateTime;

/**
 * FileUrlResp
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/25 21:02
 */
public record FileUrlResp(
  String url,         // 完整的带 Token 的链接
  OffsetDateTime expireAt    // 过期时间，方便前端展示倒计时
) {
}
