package com.example.file.domain.gateway;

import java.io.InputStream;

/**
 * 目标模板存储网关 SPI。
 * <p>
 * 由基础设施层实现，负责根据 targetTemplateId 加载出站 Excel 模板。
 * 查找优先级：数据库 → classpath 默认模板。
 */
public interface TargetTemplateStorage {

  /**
   * 加载目标模板文件流。
   * 调用方必须 try-with-resources 关闭流。
   *
   * @param targetTemplateId 目标模板标识
   * @return 模板文件输入流
   */
  InputStream load(String targetTemplateId);
}
