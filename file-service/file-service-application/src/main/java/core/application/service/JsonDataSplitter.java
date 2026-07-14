package core.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonDataSplitter {
  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * 根据明细中的某些字段拆分 JSON
   *
   * @param originalJson  引擎输出的原始完整 JSON
   * @param splitByFields 需要作为拆分依据的明细字段 (如 ["branchNo", "empCategory"])
   * @return Map<特征名, 新的JSON字符串>
   */
  public static Map<String, String> split(String originalJson, List<String> splitByFields) throws Exception {
    JsonNode root = mapper.readTree(originalJson);
    JsonNode header = root.path("header");
    JsonNode details = root.path("details");

    if (!details.isArray()) {
      return Map.of("default", originalJson);
    }

    // 核心拆分逻辑：遍历数组，通过提取字段组合成 Key，分发到不同的 ArrayNode 中
    Map<String, ArrayNode> groupedMap = new HashMap<>();

    for (JsonNode row : details) {
      String groupKey = splitByFields.stream()
        .map(field -> row.path(field).asText("UNKNOWN"))
        .collect(Collectors.joining("-")); // 如 "北京分公司-内勤"

      groupedMap.computeIfAbsent(groupKey, k -> mapper.createArrayNode()).add(row);
    }

    // 重组为标准的 Result JSON
    Map<String, String> resultMap = new HashMap<>();
    for (Map.Entry<String, ArrayNode> entry : groupedMap.entrySet()) {
      ObjectNode newRoot = mapper.createObjectNode();
      newRoot.set("header", header);        // 复制共用的表头
      newRoot.set("details", entry.getValue()); // 挂载属于该分组的明细
      resultMap.put(entry.getKey(), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(newRoot));
    }

    return resultMap;
  }
}
