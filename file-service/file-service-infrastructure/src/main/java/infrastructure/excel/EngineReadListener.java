package infrastructure.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import core.domain.model.ErrorRecord;
import core.domain.model.InboundRule;
import core.domain.model.InboundTemplate;
import core.domain.outbound.SchemaValidatorPort;
import core.domain.rule.DetailMapping;
import core.domain.rule.FieldType;
import core.domain.rule.HeaderMapping;
import infrastructure.json.JsonTreeBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 引擎核心读取监听器 (基于 SAX 流式解析与状态机流转)
 */
public class EngineReadListener extends AnalysisEventListener<Map<Integer, String>> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  // 1. 在类成员中新增哈希字典，记录 [联合Key -> 所在的Excel行号集合]
  private final Map<String, List<Integer>> uniqueCheckMap = new HashMap<>();
  private final Map<String, String> fieldLabelMap = new HashMap<>();
  // 引擎依赖与配置
  private final InboundTemplate inboundTemplate;
  private final SchemaValidatorPort validator;
  private final InboundRule inboundRule;
  private final String currentFileName; // 用于跨文件防重
  // 解析状态流转缓存
  private final JsonTreeBuilder headerBuilder;       // 头信息构建器
  private final ArrayNode detailArrayNode;           // 明细数据数组
  private final List<ErrorRecord> detailErrors;      // 收集到的明细错误
  // 性能优化：提前计算好需要拦截的列索引
  private final Set<Integer> definedDetailColIndexes;
  private boolean isFinished = false;

  public EngineReadListener(InboundTemplate inboundTemplate, SchemaValidatorPort validator, String fileName) {
    this.inboundTemplate = inboundTemplate;
    this.validator = validator;
    this.inboundRule = inboundTemplate.inboundRule();
    this.currentFileName = fileName;

    this.headerBuilder = new JsonTreeBuilder();
    this.detailArrayNode = MAPPER.createArrayNode();
    this.detailErrors = new ArrayList<>();

    // 预先计算出显式定义的强/弱类型列索引，剩下的都算作动态列 (Dynamic Columns)
    this.definedDetailColIndexes = inboundRule.detailZone().fields().stream()
      .map(mapping -> CoordinateUtils.colNameToIndex(mapping.col()))
      .collect(Collectors.toSet());

    // 2. 初始化翻译字典 (将 "$.details[*].salary" 映射为 "$.salary" -> "上年度工资")
    for (DetailMapping mapping : inboundRule.detailZone().fields()) {
      if (mapping.label() != null) {
        // 提取 JSON Path 的叶子节点，匹配 Schema Validator 的输出格式
        String leafPath = extractLeafPath(mapping.jsonPath());
        fieldLabelMap.put(leafPath, mapping.label());
      }
    }
  }

  /**
   * 逐行读取核心逻辑 (状态机)
   */
  @Override
  public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
    // EasyExcel 内部索引是 0-based。配置表中的 "endRow: 3" 表示 Excel 第3行，对应索引为 2
    int rowIndex = context.readRowHolder().getRowIndex();
    int configRowIndex = rowIndex + 1; // 转换为给业务用户看的 1-based 物理行号

    // 阶段 1：解析头信息 (HEADER_PARSING)
    if (configRowIndex <= inboundRule.headerZone().endRow()) {
      processHeader(rowIndex, rowData);
    }

    // 阶段 2：触发 Fail-Fast 头信息校验 (HEADER_VALIDATING)
    // 当刚刚读完表头区域的最后一行时，立即触发校验
    if (configRowIndex == inboundRule.headerZone().endRow()) {
      String headerJsonPayload = headerBuilder.build().toString();
      // 如果校验失败，这里会抛出 HeaderValidationException，直接打断 EasyExcel 的读取流程！
      validator.validateHeaderStrict(headerJsonPayload, inboundTemplate.jsonSchema());
    }

    // 阶段 3：解析与校验明细信息 (DETAIL_PARSING & Accumulate)
    if (configRowIndex >= inboundRule.detailZone().startRow()) {
      if (isFinished) {
        return; // 如果已经结束，直接跳过后续行
      }

      // 检查是否遇到结束标志
      String endMarker = inboundRule.detailZone().endRowMarker();
      if (endMarker != null && rowData.containsValue(endMarker)) {
        isFinished = true;
        return;
      }

      processDetail(configRowIndex, rowData);
    }
  }

  @Override
  public void doAfterAllAnalysed(AnalysisContext context) {
    for (Map.Entry<String, List<Integer>> entry : uniqueCheckMap.entrySet()) {
      List<Integer> rows = entry.getValue();
      if (rows.size() > 1) {
        String rowStr = rows.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String msg = String.format("发现重复数据！重复行号：[%s]，重复特征值：[%s]", rowStr, entry.getKey());
        // 将重复错误强行塞入到总错误集中
        detailErrors.add(new ErrorRecord(-1, "UNIQUE_KEY", msg, core.domain.model.ErrorPhase.DETAIL_VALIDATION));
      }
    }
  }

  /**
   * 获取最终聚合的数据与错误（供 App Service 层调用）
   */
  public ObjectNode getParsedDataTree() {
    ObjectNode root = MAPPER.createObjectNode();
    // 将头信息和明细数组缝合在一起
    root.set("header", headerBuilder.build().path("header"));
    root.set("details", detailArrayNode);
    return root;
  }

  public List<ErrorRecord> getDetailErrors() {
    return List.copyOf(detailErrors);
  }

  /* ---------------- 内部处理逻辑 ---------------- */

  // 辅助方法：提取叶子路径，例如 "$.details[*].salary" -> "$.salary"
  private String extractLeafPath(String fullPath) {
    int lastDot = fullPath.lastIndexOf('.');
    if (lastDot != -1) {
      return "$." + fullPath.substring(lastDot + 1);
    }
    return fullPath;
  }

  private void processHeader(int excelRowIndex, Map<Integer, String> rowData) {
    for (HeaderMapping mapping : inboundRule.headerZone().fields()) {
      // 检查当前行是否包含该 Header 配置的坐标
      if (CoordinateUtils.getRowIndex(mapping.cell()) == excelRowIndex) {
        int colIndex = CoordinateUtils.getColIndex(mapping.cell());
        String cellValue = rowData.get(colIndex);
        // 放入 JSON 构建树
        headerBuilder.put(mapping.jsonPath(), cellValue, mapping.type());
      }
    }
  }

  private void processDetail(int configRowIndex, Map<Integer, String> rowData) {
    // 如果该行为空，直接跳过 (防脏数据)
    if (rowData == null || rowData.isEmpty() || rowData.values().stream().allMatch(String::isBlank)) {
      return;
    }

    JsonTreeBuilder rowBuilder = new JsonTreeBuilder();
    Map<String, Object> dynamicAttributes = new HashMap<>();

    // 1. 处理静态定义的强弱类型列
    for (DetailMapping mapping : inboundRule.detailZone().fields()) {
      int colIndex = CoordinateUtils.colNameToIndex(mapping.col());
      String cellValue = rowData.get(colIndex);
      rowBuilder.put(mapping.jsonPath(), cellValue, mapping.type());
    }

    // 2. 处理动态扩展列 (Dynamic Fields)
    if (inboundRule.detailZone().dynamicFields() != null) {
      int startDynamicColIndex = CoordinateUtils.colNameToIndex(
        inboundRule.detailZone().dynamicFields().startCol()
      );

      // 遍历所有有数据的列，如果 >= 动态开始列，且没有被显式定义过，就丢入兜底 Map 中
      for (Map.Entry<Integer, String> entry : rowData.entrySet()) {
        int colIndex = entry.getKey();
        if (colIndex >= startDynamicColIndex && !definedDetailColIndexes.contains(colIndex)) {
          // 可以根据业务约定，使用列名如 "C", "D" 或者取表头名称作为 Key
          // 这里为了灵活，先使用 Excel 原生列名字母作为键名
          String colName = excelColIndexToName(colIndex);
          dynamicAttributes.put(colName, entry.getValue());
        }
      }
      rowBuilder.putMap(inboundRule.detailZone().dynamicFields().jsonPath(), dynamicAttributes);
    }

    // 3. 注入系统元数据：当前物理行号
    rowBuilder.put("$._meta.fileName", currentFileName, FieldType.WEAK);
    rowBuilder.put("$._meta.rowIndex", configRowIndex, FieldType.WEAK);

    // 4. 获取当前行 JSON 并执行 Accumulate 校验
    String detailJson = rowBuilder.build().toString();
    List<ErrorRecord> rowErrors = validator.validateDetailRow(detailJson, inboundTemplate.jsonSchema(), configRowIndex);

    if (inboundRule.detailZone().uniqueKeys() != null && !inboundRule.detailZone().uniqueKeys().isEmpty()) {
      JsonNode currentRowNode = rowBuilder.build();
      String combinedKey = inboundRule.detailZone().uniqueKeys().stream()
        .map(key -> currentRowNode.path(key).asText("null"))
        .collect(Collectors.joining("|"));
      uniqueCheckMap.computeIfAbsent(combinedKey, k -> new ArrayList<>()).add(configRowIndex);
    }

    if (rowErrors.isEmpty()) {
      // 校验成功，加入有效结果树
      detailArrayNode.add(rowBuilder.build());
    } else {
      // 校验失败，收集错误继续执行，丢弃（或保留，取决于业务，通常不合格数据不进入核心）脏数据
      // detailErrors.addAll(rowErrors);
      // 也可以选择依然加入 arrayNode，仅标记 _meta.hasError = true。这里我们选择只保留成功数据。
      // 拦截并翻译错误信息
      for (ErrorRecord err : rowErrors) {
        String translatedMsg = err.message();
        String errorPath = err.cell(); // 诸如 "$.salary"

        // 1. 替换字段名为中文
        if (fieldLabelMap.containsKey(errorPath)) {
          String cnLabel = "【" + fieldLabelMap.get(errorPath) + "】";
          translatedMsg = translatedMsg.replace(errorPath, cnLabel);
        }

        // 2. 顺手做一波常见内置英文 Schema 错误的强行汉化（极大地提升用户体验）
        translatedMsg = translatedMsg.replace("must be number", "必须是数字格式");
        translatedMsg = translatedMsg.replace("must be integer", "必须是整数");
        translatedMsg = translatedMsg.replace("is missing but it is required", "是必填项，不能为空");
        translatedMsg = translatedMsg.replace("must have a minimum length of", "字符长度不能短于");
        // 可以根据业务遇到过哪些报错，在这里不断累加常用翻译

        // 将翻译后的错误塞入集合
        detailErrors.add(new ErrorRecord(
          err.rowIndex(),
          err.cell(),
          translatedMsg,
          err.phase()
        ));
      }
    }
  }

  // 将 0-based 下标逆向转为字母，如 2 -> "C" (用于动态列命名)
  private String excelColIndexToName(int colIndex) {
    StringBuilder colName = new StringBuilder();
    int temp = colIndex + 1;
    while (temp > 0) {
      int remainder = (temp - 1) % 26;
      colName.insert(0, (char) (remainder + 'A'));
      temp = (temp - 1) / 26;
    }
    return colName.toString();
  }
}
