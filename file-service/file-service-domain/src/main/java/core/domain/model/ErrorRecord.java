package core.domain.model;

/**
 * 错误记录值对象
 *
 * @param rowIndex 发生错误的 Excel 绝对行号
 * @param cell     发生错误的列/单元格坐标 (可能为空，如果是整行校验错误)
 * @param message  错误原因
 * @param phase    错误发生的阶段
 */
public record ErrorRecord(
  int rowIndex,
  String cell,
  String message,
  ErrorPhase phase
) {
}
