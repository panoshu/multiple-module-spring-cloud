package infrastructure.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import core.domain.exception.HeaderValidationException;
import core.domain.model.ErrorPhase;
import core.domain.model.ErrorRecord;
import core.domain.outbound.SchemaValidatorPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 基于 Networknt 的 JSON Schema 校验器适配器
 */
public class NetworkntSchemaValidatorAdapter implements SchemaValidatorPort {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final JsonSchemaFactory FACTORY = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

  @Override
  public void validateHeaderStrict(String headerJsonNode, String jsonSchemaConfig) {
    try {
      JsonNode rootSchemaNode = MAPPER.readTree(jsonSchemaConfig);
      // 魔法：仅提取 header 对应的子 Schema。假设标准契约中头信息在 "properties" -> "header" 下
      JsonNode headerSchemaNode = rootSchemaNode.path("properties").path("header");
      if (headerSchemaNode.isMissingNode()) {
        return; // 如果根本没有定义 header 的校验规则，直接放行
      }

      JsonSchema headerSchema = FACTORY.getSchema(headerSchemaNode);
      JsonNode targetData = MAPPER.readTree(headerJsonNode).path("header"); // 取出组装好的 header 节点

      Set<ValidationMessage> errors = headerSchema.validate(targetData);

      if (!errors.isEmpty()) {
        List<ErrorRecord> errorRecords = errors.stream()
          .map(msg -> new ErrorRecord(
            -1, // 表头是多行混合，这里无法精确定位哪一行，通常标为 -1，或通过 cell 字段追溯
            msg.getInstanceLocation().toString(),
            msg.getMessage(),
            ErrorPhase.HEADER_VALIDATION))
          .toList();

        // 触发 Fail-Fast
        throw new HeaderValidationException(errorRecords);
      }
    } catch (Exception e) {
      if (e instanceof HeaderValidationException) {
        throw (HeaderValidationException) e;
      }
      throw new RuntimeException("表头 JSON Schema 解析/校验失败", e);
    }
  }

  @Override
  public List<ErrorRecord> validateDetailRow(String detailJsonNode, String jsonSchemaConfig, int rowIndex) {
    List<ErrorRecord> resultErrors = new ArrayList<>();
    try {
      JsonNode rootSchemaNode = MAPPER.readTree(jsonSchemaConfig);
      // 魔法：提取单行明细的子 Schema。路径为 "properties" -> "details" -> "items"
      JsonNode detailItemSchemaNode = rootSchemaNode.path("properties").path("details").path("items");
      if (detailItemSchemaNode.isMissingNode()) {
        return resultErrors; // 没有明细校验规则，直接放行
      }

      JsonSchema detailSchema = FACTORY.getSchema(detailItemSchemaNode);
      JsonNode targetData = MAPPER.readTree(detailJsonNode); // 这里传入的已经是单行的标准 JSON 了

      Set<ValidationMessage> errors = detailSchema.validate(targetData);

      for (ValidationMessage msg : errors) {
        // Accumulate: 收集所有的行内错误，并且注入了具体的绝对 rowIndex
        resultErrors.add(new ErrorRecord(
          rowIndex,
          msg.getInstanceLocation().toString(), // 会返回类似 "$.skuCode" 的路径
          msg.getMessage(),
          ErrorPhase.DETAIL_VALIDATION
        ));
      }
    } catch (Exception e) {
      resultErrors.add(new ErrorRecord(rowIndex, "ROW", "行数据格式严重错误，无法进行 Schema 校验", ErrorPhase.DETAIL_VALIDATION));
    }
    return resultErrors;
  }
}
