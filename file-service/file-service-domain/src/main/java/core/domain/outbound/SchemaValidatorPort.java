package core.domain.outbound;

import core.domain.model.ErrorRecord;

import java.util.List;

/**
 * JSON Schema 校验端口 (SPI)
 * 桥接底层的 JSON Schema 验证器引擎
 */
public interface SchemaValidatorPort {

  /**
   * 仅对 Header 的 JSON 节点进行 Fast-fail 校验
   * 如果失败，内部应当抛出 HeaderValidationException
   */
  void validateHeaderStrict(String headerJsonNode, String jsonSchemaConfig);

  /**
   * 对单行 Detail 的 JSON 节点进行校验
   * 不抛出异常，而是收集错误信息用于 Accumulate 容错
   *
   * @param detailJsonNode 单行数据的 JSON
   * @param rowIndex       当前行号，用于构建 ErrorRecord
   * @return 如果校验通过返回空列表，否则返回错误集
   */
  List<ErrorRecord> validateDetailRow(String detailJsonNode, String jsonSchemaConfig, int rowIndex);
}
