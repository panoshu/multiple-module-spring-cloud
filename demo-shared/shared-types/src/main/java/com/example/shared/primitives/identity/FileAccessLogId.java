package com.example.shared.primitives.identity;

/**
 * 文件访问流水 ID
 * <p>
 * 唯一标识一条文件访问（APPLY / ACCESS）流水记录。
 * 与 {@link FileId} 区分：FileId 标识文件本体，FileAccessLogId 标识访问流水。
 */
@IdDefinition(type = IdType.ULID)
public record FileAccessLogId(String value) implements Identifier<String> {

    public FileAccessLogId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("FileAccessLogId cannot be null or blank.");
        }
    }

    public static FileAccessLogId of(String value) {
        return new FileAccessLogId(value);
    }
}
