package com.example.file.application.command;

import com.example.file.domain.model.aggregate.valueobject.FileAccessScope;
import com.example.shared.identifier.id.FileId;
import com.example.shared.identifier.id.UserNo;

import java.time.Duration;

/**
 * 申请下载 Token 命令
 * <p>
 * ttl 必须由调用方（adapter/controller）显式传入，UseCase 不依赖 file-infrastructure
 * 的 FileTokenProperties（避免 application 反向依赖 infrastructure 形成循环依赖）。
 *
 * @param fileId      要下载的文件 ID
 * @param sourceApp   来源应用
 * @param accessScope 文件访问范围（企业 + 产品）
 * @param downloader  下载人
 * @param ttl         token 有效期（必传，调用方从 FileTokenProperties 读取后传入）
 */
public record ApplyDownloadTokenCommand(
  FileId fileId,
  String sourceApp,
  FileAccessScope accessScope,
  UserNo downloader,
  Duration ttl
) {
}
