package com.example.shared.primitives.identity;

/**
 * 应用 ID
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:43
 */
@IdDefinition(type = IdType.ULID)
public record ApplicationId(String value) implements Identifier<String> {
}
