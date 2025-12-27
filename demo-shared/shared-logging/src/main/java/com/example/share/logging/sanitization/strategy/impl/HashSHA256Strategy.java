package com.example.share.logging.sanitization.strategy.impl;

import com.example.share.logging.sanitization.properties.SanitizationStrategyType;
import com.example.share.logging.sanitization.strategy.param.HashSHA256Params;
import com.example.share.logging.sanitization.strategy.param.StrategyParams;
import com.example.share.logging.sanitization.strategy.SanitizationStrategy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2025/12/23 17:24
 */
@Slf4j
public class HashSHA256Strategy implements SanitizationStrategy {

  // 使用常量定义算法和返回长度
  private static final String ALGORITHM = "SHA-256";
  private static final int HASH_LENGTH = 64;  // SHA-256 产生 64 位十六进制字符串
  private static final String ERROR_PLACEHOLDER = "*".repeat(HASH_LENGTH);

  // 使用静态的 ThreadLocal 缓存 MessageDigest 实例，提高性能
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_CACHE =
    ThreadLocal.withInitial(() -> {
      try {
        return MessageDigest.getInstance(ALGORITHM);
      } catch (NoSuchAlgorithmException e) {
        log.error("{} algorithm not available", ALGORITHM, e);
        throw new IllegalStateException("SHA-256 algorithm not available", e);
      }
    });

  @Override
  public SanitizationStrategyType getType() {
    return SanitizationStrategyType.HASH_SHA256;  // 注意：类型名称可能需要更新
  }

  @Override
  public String sanitize(String value, StrategyParams params) {
    if (!supports(value)) {
      return value;
    }

    // HASH_SHA256策略可以接受null或HashSHA256Params
    return switch (params) {
      case HashSHA256Params ignored -> hashValue(value);
      case null -> throw new IllegalArgumentException(
        "HashSHA256Strategy requires HashSHA256Params, but got null");
      default -> throw new IllegalArgumentException(
        String.format("HashSHA256Strategy requires HashSHA256Params or null, but got %s",
          params.getClass().getSimpleName()));
    };
  }

  private @NonNull String hashValue(String value) {
    try {
      MessageDigest md = MESSAGE_DIGEST_CACHE.get();
      md.reset();

      byte[] hashBytes = md.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hashBytes);
    } catch (Exception e) {
      log.warn("Failed to hash value with {}, using placeholder", ALGORITHM, e);
      return ERROR_PLACEHOLDER;
    }
  }
}
