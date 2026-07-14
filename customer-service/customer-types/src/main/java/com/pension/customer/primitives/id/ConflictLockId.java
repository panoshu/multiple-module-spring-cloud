package com.pension.customer.primitives.id;

import com.example.shared.primitives.identity.IdDefinition;
import com.example.shared.primitives.identity.IdType;
import com.example.shared.primitives.identity.Identifier;

/**
 * ConflictLockId
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/10 11:03
 */
@IdDefinition(type = IdType.ULID)
public record ConflictLockId(String value) implements Identifier<String> {
}
