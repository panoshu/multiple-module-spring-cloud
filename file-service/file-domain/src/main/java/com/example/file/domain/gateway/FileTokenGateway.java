package com.example.file.domain.gateway;

import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;

/**
 * 文件 Token 加密网关 SPI
 * <p>
 * 由 KonaFileTokenGateway 实现，使用国密 SM4 算法。
 */
public interface FileTokenGateway {

  /**
   * 加密 token 载荷，返回密文字符串
   */
  String encrypt(FileTokenPayload payload);

  /**
   * 解密 token 字符串，返回载荷
   * 解密失败或格式错误抛 SystemException(FILE_TOKEN_INVALID)
   */
  FileTokenPayload decrypt(String token);
}
