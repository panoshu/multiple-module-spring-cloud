package com.example.core.types;

import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

/**
 * 文件引用 ID（kernel 自有文件标识）。
 * <p>
 * kernel 的领域模型不直接依赖具体文件服务（file-service）的类型；文件服务的
 * {@code FileId} 只在基础设施适配层与本类型互相转换。这样替换文件实现或独立
 * 复用 kernel 时，领域与应用层无需改动。
 *
 * @author business-core-kernel
 */
@IdDefinition(type = IdType.ULID)
public record FileRefId(String value) implements Identifier<String> {

  public FileRefId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FileRefId cannot be null or blank.");
    }
  }

  public static FileRefId of(String value) {
    return new FileRefId(value);
  }
}
