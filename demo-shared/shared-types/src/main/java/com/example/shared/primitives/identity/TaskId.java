package com.example.shared.primitives.identity;

/**
 * 业务流水号 ID
 * 定义：
 * 1. 基础名: BIZ
 * 2. 格式: 前缀 + 日期 + 序号
 */
@IdDefinition(name = "BIZ", format = "%p%d%s", seqKey = "%d_%n", dateFormat = "yyyyMMdd")
public record TaskId(String value) implements Identifier<String> {
}
