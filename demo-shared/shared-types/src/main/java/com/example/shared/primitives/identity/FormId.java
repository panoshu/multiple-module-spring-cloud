package com.example.shared.primitives.identity;

/**
 * 领域事件ID
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/9 12:43
 */
@IdDefinition(type = IdType.ULID)
public record FormId(String value) implements Identifier<String> {
}
