package com.example.file.application.access;

import java.time.Duration;

/**
 * 文件 Token 默认有效期策略（F-01）。
 * <p>
 * adapter 只依赖本应用层端口读取默认 TTL，不再直接依赖
 * {@code file-infrastructure} 的 {@code FileTokenProperties}；
 * 配置绑定仍由 infrastructure 完成。
 */
public interface FileTokenPolicy {

  Duration defaultUploadTtl();

  Duration defaultDownloadTtl();
}
