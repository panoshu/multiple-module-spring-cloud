package infrastructure.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import core.domain.rule.FieldType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 动态 JSON 树构建器 (基于 Jackson)
 * 负责将扁平的 Excel 提取值，按照 jsonPath 组合为树状的 ObjectNode。
 */
public class JsonTreeBuilder {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final ObjectNode root;

  public JsonTreeBuilder() {
    this.root = MAPPER.createObjectNode();
  }

  /**
   * 插入单个属性
   *
   * @param jsonPath 如 "$.header.orderNo" 或者 "skuCode" (明细行去掉数组前缀后)
   * @param value    实际值
   * @param type     强弱类型 (决定了转换策略)
   */
  public void put(String jsonPath, Object value, FieldType type) {
    if (value == null) {
      return;
    }

    // 1. 清理 JsonPath 常见的前缀标识 "$."
    String cleanPath = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
    // 2. 对于明细行配置如 "details[*].skuCode"，在单行构建时我们只需要属性名 "skuCode"
    cleanPath = cleanPath.replaceAll("^.*\\[\\*]\\.", "");

    String[] parts = cleanPath.split("\\.");
    ObjectNode currentNode = root;

    // 3. 级联创建父级 ObjectNode
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      if (currentNode.get(part) == null || !currentNode.get(part).isObject()) {
        currentNode.set(part, MAPPER.createObjectNode());
      }
      currentNode = (ObjectNode) currentNode.get(part);
    }

    // 4. 赋值叶子节点
    String leafKey = parts[parts.length - 1];
    setLeafNodeValue(currentNode, leafKey, value, type);
  }

  /**
   * 合并一个动态 Map (专门用于处理 Weak 类型的动态扩展列)
   */
  public void putMap(String jsonPath, Map<String, Object> dynamicValues) {
    if (dynamicValues == null || dynamicValues.isEmpty()) {
      return;
    }

    String cleanPath = jsonPath.startsWith("$.") ? jsonPath.substring(2) : jsonPath;
    cleanPath = cleanPath.replaceAll("^.*\\[\\*]\\.", "");

    ObjectNode mapNode = MAPPER.valueToTree(dynamicValues);
    root.set(cleanPath, mapNode);
  }

  /**
   * 获取构建好的当前节点
   */
  public JsonNode build() {
    return root;
  }

  private void setLeafNodeValue(ObjectNode node, String key, Object value, FieldType type) {
    if (type == FieldType.STRONG && value instanceof String s) {
      String trimmed = s.trim();
      // 1. 尝试转为布尔值
      if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
        node.put(key, Boolean.parseBoolean(trimmed));
        return;
      }
      // 2. 尝试转为数字 (如果有小数点使用 BigDecimal，否则用 Long 兼容 Integer)
      try {
        if (trimmed.contains(".")) {
          node.put(key, new java.math.BigDecimal(trimmed));
        } else {
          node.put(key, Long.parseLong(trimmed));
        }
        return;
      } catch (NumberFormatException e) {
        // 如果抛出异常，说明它确实是个普通字符串，放行交给下面处理
      }
    }
    // 利用 JDK 21+ 的模式匹配特性处理强类型转换
    switch (value) {
      case String s -> node.put(key, s);
      case Integer i -> node.put(key, i);
      case Long l -> node.put(key, l);
      case Double d -> node.put(key, d);
      case BigDecimal bd -> node.put(key, bd);
      case Boolean b -> node.put(key, b);
      default -> node.putPOJO(key, value); // 弱类型的其他对象兜底
    }
  }
}
