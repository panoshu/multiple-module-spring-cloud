package com.example.file.application.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Token 哈希工具类
 * <p>
 * 用于将 token 转换为不可逆的 SHA-256 哈希后存入 FileAccessLog，
 * 避免明文 token 落库，同时保留同一 token 的关联性。
 */
public final class TokenHashUtil {

  private TokenHashUtil() {
  }

  /**
   * 计算输入字符串的 SHA-256 哈希（小写十六进制）。
   * 异常时返回常量 "sha256-error"，避免抛出影响主流程。
   */
  public static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (byte b : hash) {
        hex.append(String.format("%02x", b));
      }
      return hex.toString();
    } catch (Exception e) {
      return "sha256-error";
    }
  }
}
