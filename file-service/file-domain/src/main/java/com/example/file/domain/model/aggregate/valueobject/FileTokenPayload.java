package com.example.file.domain.model.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Token 明文载荷（不持久化）
 *
 * @param tokenId             token 唯一 ID（UUID）
 * @param fileId              文件 ID
 * @param usage               用途（SOURCE 上传 / EXPORT 下载等）
 * @param bizType             业务类型
 * @param customerNo          企业编号
 * @param productNo           产品编号
 * @param operator            操作人（uploader 或 downloader）
 * @param allowedContentTypes 允许的文件类型（上传 token 专有，下载为 null）
 * @param allowedMaxSize      允许的最大文件大小（上传 token 专有，下载为 null）
 * @param expireAt            过期时间
 */
public record FileTokenPayload(
  String tokenId,
  FileId fileId,
  FileUsage usage,
  String bizType,
  CustomerNo customerNo,
  ProductNo productNo,
  UserNo operator,
  List<String> allowedContentTypes,
  Long allowedMaxSize,
  LocalDateTime expireAt
) implements ValueObject {
  public FileTokenPayload {
    if (tokenId == null || tokenId.isBlank()) throw new IllegalArgumentException("tokenId 不能为空");
    if (fileId == null) throw new IllegalArgumentException("fileId 不能为空");
    if (usage == null) throw new IllegalArgumentException("usage 不能为空");
    if (customerNo == null) throw new IllegalArgumentException("customerNo 不能为空");
    if (productNo == null) throw new IllegalArgumentException("productNo 不能为空");
    if (operator == null) throw new IllegalArgumentException("operator 不能为空");
    if (expireAt == null) throw new IllegalArgumentException("expireAt 不能为空");
  }
}
