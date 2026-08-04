package com.example.core.domain.business.aggregate.valueobject;

import com.example.shared.identifier.id.FileId;

/**
 * 业务文件附件
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 23:01
 */
public record BusinessFile(
  FileId fileId,        // 文件服务返回的唯一标识
  String fileName,      // 用户上传时的真实文件名 (展示用)
  String extension,     // 文件扩展名 (如 pdf, jpg，用于前端图标展示或校验)
  Long fileSizeBytes    // 文件大小 (可选，用于限制检查)
) {
  // 可以在这里加上一些校验逻辑，比如 isImage(), isPdf() 等
}
