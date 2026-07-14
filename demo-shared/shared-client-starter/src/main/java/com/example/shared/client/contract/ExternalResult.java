package com.example.shared.client.contract;


/**
 * 业务结果统一接口
 * 各外部系统的 Result 都需实现此接口
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/2 15:03
 */
public interface ExternalResult<T> {
  boolean isSuccess();

  String getErrorCode();

  String getErrorMsg();

  T getData();
}
