package core.domain.model;

public enum ParseStatus {
  SUCCESS,          // 完全成功
  PARTIAL_SUCCESS,  // 头信息成功，但部分明细行发生错误
  FAILED            // 发生致命错误 (如头信息校验失败)
}
