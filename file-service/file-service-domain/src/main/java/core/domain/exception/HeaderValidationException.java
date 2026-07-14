package core.domain.exception;

import core.domain.model.ErrorRecord;

import java.util.List;

/**
 * 表头校验异常 (Fail-Fast 触发器)
 * 此异常一旦抛出，将直接打断底层的流式解析
 */
public class HeaderValidationException extends RuntimeException {
  private final List<ErrorRecord> headerErrors;

  public HeaderValidationException(List<ErrorRecord> headerErrors) {
    super("表头信息校验失败，解析中断！");
    this.headerErrors = headerErrors;
  }

  public List<ErrorRecord> getHeaderErrors() {
    return headerErrors;
  }
}
