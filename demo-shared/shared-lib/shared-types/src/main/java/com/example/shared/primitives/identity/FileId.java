package com.example.shared.primitives.identity;

/**
 * 文件 ID
 * <p>
 * 唯一标识一个文件本体，与 {@link FileAccessLogId}（文件访问流水 ID）区分。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:43
 */
@IdDefinition(type = IdType.ULID)
public record FileId(String value) implements Identifier<String> {

  public FileId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FileId cannot be null or blank.");
    }
  }

  public static FileId of(String value) {
    return new FileId(value);
  }
}
