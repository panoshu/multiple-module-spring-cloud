package com.pension.customer.primitives.id;

import com.example.shared.primitives.identity.IdDefinition;
import com.example.shared.primitives.identity.Identifier;


/**
 * 业务流水号 ID
 * 定义：
 * 1. 基础名: BIZ
 * 2. 格式: 前缀 + 日期 + 序号
 */
@IdDefinition(name = "EVENT", format = "%p%d%s", seqKey = "%d_%n", dateFormat = "yyyyMMdd")
public record EventId(String value) implements Identifier<String> {
}
