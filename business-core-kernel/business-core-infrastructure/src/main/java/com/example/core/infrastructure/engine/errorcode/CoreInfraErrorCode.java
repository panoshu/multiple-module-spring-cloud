package com.example.core.infrastructure.engine.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;

/**
 * business-core-infrastructure 模块错误码定义。
 * <p>
 * 错误码区间 {@code CORE.INFRA.0001-CORE.INFRA.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：CORE.INFRA.XXXX（业务核心模块 - business-core-infrastructure）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 * <p>
 * 用于 {@code SystemException} 的错误码定义，描述与外部服务集成（文件下载/审批查询等）
 * 相关的系统级故障。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/23
 */
@AllArgsConstructor
public enum CoreInfraErrorCode implements ErrorDefinition {

  FILE_DOWNLOAD_FAILED("CORE.INFRA.0001", "文件下载失败"),
  FILE_TOKEN_APPLY_FAILED("CORE.INFRA.0002", "文件下载令牌申请失败"),
  ;

  final String code;
  final String message;

  @Override
  public String code() {
    return this.code;
  }

  @Override
  public String message() {
    return this.message;
  }
}
