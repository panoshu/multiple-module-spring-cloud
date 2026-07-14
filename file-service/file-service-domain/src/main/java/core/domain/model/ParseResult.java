package core.domain.model;

import java.util.List;

/**
 * 解析结果聚合根 (不可变)
 */
public record ParseResult(
  String jsonPayload,       // 解析成功并组装好的 JSON 树 (包含头信息和明细信息)
  ParseStatus status,       // 整体状态
  List<ErrorRecord> errors  // 收集到的错误明细（明细行的校验错误）
) {
  public boolean isSuccess() {
    return status == ParseStatus.SUCCESS;
  }

  public ParseResult withStatus(ParseStatus status) {
    return new ParseResult(this.jsonPayload, status, this.errors);
  }
}
