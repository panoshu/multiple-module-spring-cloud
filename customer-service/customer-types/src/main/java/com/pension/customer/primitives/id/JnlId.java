package com.pension.customer.primitives.id;

import com.example.shared.primitives.identity.IdDefinition;
import com.example.shared.primitives.identity.Identifier;

/**
 * 全局流水号
 * <p>
 * globalSequence = true: 强制所有业务共用 "GLOBAL_SEQ" 这个计数器
 * format = "%s": 格式为 序号 纯数字
 */
@IdDefinition(format = "%n%s", seqKey = "%n_%d_%p", dateFormat = "yyyyMMdd")
public record JnlId(String value) implements Identifier<String> {
}
