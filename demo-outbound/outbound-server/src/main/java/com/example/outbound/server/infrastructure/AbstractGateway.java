package com.example.outbound.server.infrastructure;

import com.example.shared.core.api.IResultCode;
import com.example.shared.core.infrastructure.ExternalCallTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 网关适配器基类
 * 封装了特定渠道（如支付宝、顺丰）的通用策略
 */
public abstract class AbstractGateway<REQ, RESP> {

  @Autowired
  protected ExternalCallTemplate template;

  // —————— 子类必须实现的契约 ——————

  /**
   * 判断响应是否代表业务成功
   */
  protected abstract boolean isSuccess(RESP response);

  /**
   * 获取该渠道统一的系统异常错误码（如 EXTERNAL_SERVICE_ERROR）
   */
  protected abstract IResultCode getSystemError();

  /**
   * 获取该渠道默认的业务异常错误码（如 EXTERNAL_SERVICE_ERROR 或 BUSINESS_ERROR）
   * 用于简单场景兜底
   */
  protected abstract IResultCode getDefaultBusinessError();

  // —————— 可选覆盖的配置 ——————

  protected String getDefaultErrorPattern() {
    return "外部接口业务失败: {}";
  }

  protected Object[] extractDefaultErrorArgs(RESP response) {
    return new Object[]{ response };
  }

  // —————— 模式 A: 简单调用 (Simple Execute) ——————

  /**
   * 快捷方法：使用默认错误码、默认日志格式执行调用
   * 适用于：不需要区分具体业务错误码，或错误处理逻辑简单的场景
   */
  protected <T> T executeSimple(String action, Object bizId,
                                Supplier<REQ> reqSupplier,
                                Function<REQ, RESP> remoteCall,
                                Function<RESP, T> mapper) {
    return template.execute(
      ExternalCallTemplate.<REQ, RESP, T>builder()
        .action(action, bizId)
        .systemError(getSystemError())
        .businessError(getDefaultBusinessError())
        .checkSuccess(this::isSuccess)
        .onBusinessFailure(getDefaultErrorPattern(), this::extractDefaultErrorArgs)
        .request(reqSupplier)
        .call(remoteCall)
        .map(mapper)
    );
  }

  // —————— 模式 B: 灵活构建 (Flexible Build) ——————

  /**
   * 流式 API 入口：返回一个预填充了默认值的 Builder
   * 适用于：需要自定义错误码映射、自定义日志参数的复杂场景
   */
  protected <T> ExternalCallTemplate.ExternalCallBuilder<REQ, RESP, T> buildCall(String action, Object bizId) {
    return ExternalCallTemplate.<REQ, RESP, T>builder()
      .action(action, bizId)
      // 预填充基类定义的默认值
      .systemError(getSystemError())
      .businessError(getDefaultBusinessError())
      .checkSuccess(this::isSuccess)
      .onBusinessFailure(getDefaultErrorPattern(), this::extractDefaultErrorArgs);
  }

  // —————— 模式 C: 标准调用 (Standard Execute) ——————

  /**
   * 标准通用模板：支持指定业务错误码 + 自定义系统/业务错误详情
   * * @param bizErrorCode 指定的业务错误码 (如 OutboundErrorCode.PAY_FAILED)
   * @param bizErrorPattern 业务失败时的日志模板 "下单失败: code={}, msg={}"
   * @param bizErrorArgsExtractor 业务失败时的参数提取
   * @param sysErrorPattern 系统失败时的日志模板 (可选，传 null 则用默认)
   * @param sysErrorArgsExtractor 系统失败时的参数提取 (可选，传 null 则用默认)
   */
  protected <T> T executeStandard(String action, Object bizId,
                                  Supplier<REQ> reqSupplier,
                                  Function<REQ, RESP> remoteCall,
                                  Function<RESP, T> mapper,
                                  // 业务异常配置
                                  IResultCode bizErrorCode,
                                  String bizErrorPattern,
                                  Function<RESP, Object[]> bizErrorArgsExtractor,
                                  // 系统异常配置 (可选)
                                  String sysErrorPattern,
                                  Function<REQ, Object[]> sysErrorArgsExtractor) {

    var builder = ExternalCallTemplate.<REQ, RESP, T>builder()
      .action(action, bizId)
      .systemError(getSystemError()) // 默认系统码
      .businessError(bizErrorCode)   // 指定业务码
      .checkSuccess(this::isSuccess)
      .request(reqSupplier)
      .call(remoteCall)
      .map(mapper);

    // 配置业务异常详情
    if (bizErrorPattern != null) {
      builder.onBusinessFailure(bizErrorPattern, bizErrorArgsExtractor);
    } else {
      builder.onBusinessFailure(getDefaultErrorPattern(), this::extractDefaultErrorArgs);
    }

    // 配置系统异常详情 (如果有)
    if (sysErrorPattern != null) {
      builder.onSystemFailure(sysErrorPattern, sysErrorArgsExtractor);
    }

    return template.execute(builder);
  }

  // 重载一个简化版：只自定义业务异常详情，系统异常用默认的
  protected <T> T executeStandard(String action, Object bizId,
                                  Supplier<REQ> reqSupplier,
                                  Function<REQ, RESP> remoteCall,
                                  Function<RESP, T> mapper,
                                  IResultCode bizErrorCode,
                                  String bizErrorPattern,
                                  Function<RESP, Object[]> bizErrorArgsExtractor) {
    return executeStandard(action, bizId, reqSupplier, remoteCall, mapper,
      bizErrorCode, bizErrorPattern, bizErrorArgsExtractor,
      null, null);
  }

  /**
   * 执行由 buildCall 构建的 Builder
   */
  protected <T> T execute(ExternalCallTemplate.ExternalCallBuilder<REQ, RESP, T> builder) {
    return template.execute(builder);
  }
}
