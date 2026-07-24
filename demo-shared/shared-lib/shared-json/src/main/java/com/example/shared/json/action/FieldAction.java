package com.example.shared.json.action;

import java.util.List;

/**
 * 字段处理动作接口：对匹配的字段值执行处理（加密、解密、脱敏等）。
 *
 * <p>解耦字段处理逻辑与 JSON 遍历逻辑，支持不同处理策略：
 * <ul>
 *   <li>加解密：网关 CryptoFilter 使用 SM4 加解密</li>
 *   <li>脱敏：日志 Sanitizer 使用多种脱敏策略</li>
 *   <li>哈希：敏感字段哈希处理</li>
 * </ul>
 *
 * <p>返回值语义：
 * <ul>
 *   <li>返回非 null：用返回值替换原值（<b>注意：原字段的 JSON 类型会被统一替换为字符串</b>）</li>
 *   <li>返回 null：不修改，保留原值（含原类型）</li>
 * </ul>
 *
 * <p><b>类型变化行为</b>：当返回非 null 值时，{@link com.example.shared.json.processor.JsonFieldProcessor}
 * 会用 {@code generator.writeString(result)} 输出，导致原字段的 JSON 类型（数字、布尔、null）
 * 统一变为字符串类型。例如：
 * <ul>
 *   <li>{@code {"age":18}} → 加密后 {@code {"age":"密文"}}（数字变字符串）</li>
 *   <li>{@code {"enabled":true}} → 加密后 {@code {"enabled":"密文"}}（布尔变字符串）</li>
 * </ul>
 * 这符合加密/脱敏语义（密文和脱敏值本身就是字符串），但调用方需理解此行为。
 * 对于解密场景，解密后的值也是字符串（如 {@code "18"} 而非 {@code 18}），
 * 后端 DTO 反序列化时需依赖 Jackson 的类型转换能力，或在业务层明确加密协议。
 *
 * <p>处理失败时由实现自行决定容错策略（如记录 WARN 后返回 null 保留原值）。
 *
 * @author trae
 * @since 1.0
 */
@FunctionalInterface
public interface FieldAction {

  /**
   * 处理字段值。
   *
   * @param fieldName  字段名
   * @param pathStack  从根到当前字段父级的路径栈（正序：root → parent，不含当前字段名）
   * @param value      原始值（不会为 null）
   * @return 处理后的值；返回 null 表示不修改（保留原值）；返回非 null 会替换原值（类型变为字符串）
   */
  String process(String fieldName, List<String> pathStack, String value);
}
