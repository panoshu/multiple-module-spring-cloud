package com.example.core.infrastructure.engine.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;

/**
 * 核心编排域基础设施层错误码
 * <p>
 * 用于 {@code SystemException} 的错误码定义,描述与外部服务集成(文件下载/审批查询等)
 * 相关的系统级故障。编号区间 300xxx,与应用层(100xxx)、领域层(200xxx)区分。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/23
 */
@AllArgsConstructor
public enum CoreInfraErrorCode implements ErrorDefinition {

  FILE_DOWNLOAD_FAILED("300001", "[文件下载失败]{}"),
  FILE_TOKEN_APPLY_FAILED("300002", "[文件下载令牌申请失败]{}"),
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
