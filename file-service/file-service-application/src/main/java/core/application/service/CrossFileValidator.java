// 2. 跨文件防重校验服务 (src/main/java/core/application/service/CrossFileValidator.java)
package core.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.domain.model.ErrorPhase;
import core.domain.model.ErrorRecord;
import core.domain.model.ParseResult;
import core.domain.model.ParseStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CrossFileValidator {
  private static final ObjectMapper mapper = new ObjectMapper();

  /**
   * 跨文件全局防重校验
   */
  public static void validateDuplicates(Map<String, ParseResult> fileResultMap, List<String> uniqueKeys) {
    if (uniqueKeys == null || uniqueKeys.isEmpty()) {
      return;
    }

    // 字典结构：联合Key -> 对应出现的所有行信息 (FileName + RowIndex)
    Map<String, List<FileRowLocation>> globalUniqueMap = new HashMap<>();

    // 1. 收集所有文件中的明细数据
    for (Map.Entry<String, ParseResult> entry : fileResultMap.entrySet()) {
      ParseResult result = entry.getValue();
      if (result.jsonPayload() == null) {
        continue;
      }

      try {
        JsonNode details = mapper.readTree(result.jsonPayload()).path("details");
        if (!details.isArray()) {
          continue;
        }

        for (JsonNode row : details) {
          // 生成联合摘要 Key
          String combinedKey = uniqueKeys.stream()
            .map(k -> row.path(k).asText("null"))
            .collect(Collectors.joining("|"));

          String fileName = row.path("_meta").path("fileName").asText("Unknown");
          int rowIndex = row.path("_meta").path("rowIndex").asInt(-1);

          globalUniqueMap.computeIfAbsent(combinedKey, k -> new ArrayList<>())
            .add(new FileRowLocation(fileName, rowIndex));
        }
      } catch (Exception e) {
        // 忽略解析错误
      }
    }

    // 2. 探测碰撞并反写错误
    for (Map.Entry<String, List<FileRowLocation>> entry : globalUniqueMap.entrySet()) {
      List<FileRowLocation> locations = entry.getValue();
      if (locations.size() > 1) { // 发现重复！
        String conflictMsg = "全局重复！冲突位置: " + locations.stream()
          .map(loc -> String.format("[%s-第%d行]", loc.fileName(), loc.rowIndex()))
          .collect(Collectors.joining(", "));

        // 为所有牵扯到的文件对应的 ParseResult 追加错误
        for (FileRowLocation loc : locations) {
          ParseResult result = fileResultMap.get(loc.fileName());
          if (result != null) {
            List<ErrorRecord> newErrors = new ArrayList<>(result.errors());

            // 现在可以安全地添加错误了
            newErrors.add(new ErrorRecord(
              loc.rowIndex(),
              "GLOBAL_UNIQUE",
              conflictMsg + "。特征值: " + entry.getKey(),
              ErrorPhase.DETAIL_VALIDATION
            ));

            // 构建一个全新的 ParseResult，强制状态为 PARTIAL_SUCCESS
            ParseResult newResult = new ParseResult(
              result.jsonPayload(),
              ParseStatus.PARTIAL_SUCCESS,
              newErrors
            );

            // 直接覆盖 Map 中的旧记录 (Map 是引用传递，外层会直接生效，不需要方法有返回值)
            fileResultMap.put(loc.fileName(), newResult);
          }
        }
      }
    }
  }

  private record FileRowLocation(String fileName, int rowIndex) {
  }
}
