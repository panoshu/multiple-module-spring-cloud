package com.example.integration.domain.trade.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件提交结果（领域模型）
 * <p>
 * 表达外部交易系统对出站文件提交的响应。
 */
public record FileSubmissionResult(
  String tradeCode,
  SubmissionStatus status,
  String message,
  String externalSerialNo,
  LocalDateTime responseTime
) implements Serializable {

  /**
   * 提交状态枚举
   */
  public enum SubmissionStatus {
    SUCCESS,
    FAILED,
    TIMEOUT
  }

  /**
   * 创建成功结果
   */
  public static FileSubmissionResult success(String tradeCode, String externalSerialNo) {
    return new FileSubmissionResult(tradeCode, SubmissionStatus.SUCCESS, "提交成功",
      externalSerialNo, LocalDateTime.now());
  }

  /**
   * 创建失败结果
   */
  public static FileSubmissionResult failure(String tradeCode, String message) {
    return new FileSubmissionResult(tradeCode, SubmissionStatus.FAILED, message,
      null, LocalDateTime.now());
  }

  /**
   * 创建超时结果
   */
  public static FileSubmissionResult timeout(String tradeCode) {
    return new FileSubmissionResult(tradeCode, SubmissionStatus.TIMEOUT, "交易超时",
      null, LocalDateTime.now());
  }
}
