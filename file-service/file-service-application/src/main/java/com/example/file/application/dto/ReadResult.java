package com.example.file.application.dto;

import java.util.Collection;
import java.util.List;

public record ReadResult(
  boolean isSuccess,           // 是否全部校验通过并成功生成 JSON
  List<String> ossUrls,        // 成功时：生成的 JSON 文件在 OSS 的下载地址列表
  String errorFileUrl,         // 失败时：带错误信息的 Excel 文件的 OSS 下载地址
  List<String> globalErrors    // 全局系统级错误（如防重失败、解析崩溃）
) {

  // 成功时的静态构造工厂
  public static ReadResult success(Collection<String> ossUrls) {
    return new ReadResult(true, List.copyOf(ossUrls), null, List.of());
  }

  // 失败（存在校验错误）时的静态构造工厂
  public static ReadResult failure(String errorFileUrl) {
    return new ReadResult(false, List.of(), errorFileUrl, List.of());
  }

  // 发生系统级阻断异常时的静态构造工厂
  public static ReadResult systemError(List<String> globalErrors) {
    return new ReadResult(false, List.of(), null, List.copyOf(globalErrors));
  }
}
