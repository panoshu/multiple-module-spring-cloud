package com.example.integration.infrastructure.core.common.model;

import com.example.integration.infrastructure.errorcode.TradeErrorCode;
import com.example.shared.client.contract.ExternalResult;
import com.example.shared.exception.SystemException;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

/**
 * 统一交易响应根对象
 * 重构后与 TradeRootRequest 结构对称，字段命名统一
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/1 20:41
 */
public record TradeRootResponse<T>(
  @JsonProperty("AppResponse") AppResponse<T> appResponse
) implements ExternalResult<T>, Serializable {

  /**
   * 确保响应成功，否则抛出异常
   */
  public static <T> T requireSuccess(TradeRootResponse<T> result) {
    Objects.requireNonNull(result, "Trade API response is null");
    return result.dataOrThrow();
  }

  @JsonIgnore
  private TradeRspHead.StatusInfo statusInfo() {
    return Optional.ofNullable(appResponse)
      .map(AppResponse::head)
      .map(TradeRspHead::statusInfo)
      .orElse(null);
  }

  @JsonIgnore
  @Override
  public boolean isSuccess() {
    return "0000".equals(Optional.of(statusInfo())
      .map(TradeRspHead.StatusInfo::msgCode)
      .orElse(null));
  }

  /**
   * 获取错误码 - 仅在交易失败时返回有效错误码
   *
   * @return 错误码，成功时返回 null
   */
  @Override
  public String getErrorCode() {
    if (isSuccess()) {
      return null;
    }
    return Optional.of(statusInfo())
      .map(TradeRspHead.StatusInfo::msgCode)
      .orElse("MISSING");
  }

  /**
   * 获取错误信息 - 仅在交易失败时返回有效错误信息
   *
   * @return 错误信息，成功时返回 null
   */
  @Override
  public String getErrorMsg() {
    if (isSuccess()) {
      return null;
    }
    return Optional.of(statusInfo())
      .map(TradeRspHead.StatusInfo::msgInfo)
      .filter(s -> !s.isBlank())
      .orElse("Unknown error");
  }

  @JsonIgnore
  @Override
  public T getData() {
    if (!isSuccess()) {
      return null;
    }
    return Optional.ofNullable(appResponse)
      .map(AppResponse::body)
      .orElse(null);
  }

  private T dataOrThrow() {
    if (!isSuccess()) {
      throw new SystemException(TradeErrorCode.TRADE_API_ERROR)
        .withLogDetail("code: %s, message: %s".formatted(getErrorCode(), getErrorMsg()));
    }
    return getData();
  }

  @JsonIgnore
  public String getTradeCode() {
    return Optional.ofNullable(appResponse)
      .map(AppResponse::head)
      .map(TradeRspHead::tradeCode)
      .orElse("");
  }

  // ============ 便捷获取方法 ============

  @JsonIgnore
  public String getReqSerialNo() {
    return Optional.ofNullable(appResponse)
      .map(AppResponse::head)
      .map(TradeRspHead::reqSerialNo)
      .orElse("");
  }

  @JsonIgnore
  public TradeSource getTradeSource() {
    return Optional.ofNullable(appResponse)
      .map(AppResponse::head)
      .map(TradeRspHead::tradeSource)
      .orElse(null);
  }

  public record AppResponse<T>(
    @JsonProperty("AppRspHead") TradeRspHead head,
    @JsonProperty("AppRspBody") T body
  ) implements Serializable {
  }
}
