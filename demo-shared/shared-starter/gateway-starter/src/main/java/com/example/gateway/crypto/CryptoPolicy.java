package com.example.gateway.crypto;

import com.example.shared.json.action.FieldAction;

/**
 * 加解密策略接口：为请求解密和响应加密提供 {@link FieldAction} 实例。
 *
 * <p>职责（SRP）：封装"如何处理匹配字段的值"这一规则，与字段匹配逻辑（{@link com.example.shared.json.matcher.FieldMatcher}）
 * 和 JSON 流式遍历逻辑（{@link com.example.shared.json.processor.JsonFieldProcessor}）解耦。
 *
 * <p>扩展点（OCP）：新增策略无需修改 CryptoFilter 或 JsonFieldProcessor，只需实现此接口：
 * <ul>
 *   <li>不同接口不同字段 → 配合不同 {@link com.example.shared.json.matcher.FieldMatcher} 使用</li>
 *   <li>不同租户不同密钥 → 使用不同 {@link com.example.shared.crypto.Encryptor} 实例</li>
 *   <li>不同加密算法 → {@link com.example.shared.crypto.Encryptor} 的不同实现</li>
 *   <li>字段级策略 → 在 {@link FieldAction} 内按 fieldName/pathStack 选择不同处理方式</li>
 *   <li>不同处理类型（加密/解密/脱敏） → 返回不同的 {@link FieldAction} 实现</li>
 * </ul>
 *
 * @author trae
 * @since 1.0
 */
public interface CryptoPolicy {

  /**
   * 获取加密动作（用于响应处理：后端明文 → 前端密文）。
   *
   * @return 加密 {@link FieldAction}；返回 null 表示不处理该字段（保留原值）
   */
  FieldAction encryptAction();

  /**
   * 获取解密动作（用于请求处理：前端密文 → 后端明文）。
   *
   * @return 解密 {@link FieldAction}；返回 null 表示不处理该字段（保留原值）
   */
  FieldAction decryptAction();
}