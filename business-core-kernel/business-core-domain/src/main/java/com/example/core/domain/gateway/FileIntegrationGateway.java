package com.example.core.domain.gateway;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.FormId;

import java.io.InputStream;
import java.util.Map;

/**
 * 统一文件集成网关 (防腐层)
 * <p>
 * 屏蔽底层文件存储 (OSS/Minio) 与文档处理中心 (解析/OCR/拆分) 的技术细节。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/14 23:33
 */
public interface FileIntegrationGateway {
  /**
   * 触发异步解析：将动态规则透传给解析引擎
   *
   * @param businessMetaContext 解析规则配置（通用Map结构）
   */
  void triggerAsyncParsing(BusinessForm businessForm, BusinessMetaContext businessMetaContext);

  /**
   * 1. 触发异步表单解析与校验
   *
   * @param formId          业务表单ID (回调凭证)
   * @param sourceFileId    用户上传的原始文件
   * @param parseTemplateId 从业务配置中心查出的解析模板 ID
   * @param splitRules      业务级别的拆分与校验规则透传
   */
  void triggerAsyncParsing(FormId formId, FileId sourceFileId, String parseTemplateId, Map<String, Object> splitRules);

  /**
   * 2. 流式下载文件 (防 OOM 大杀器)
   * <p>
   * 供应用层的 AbstractJsonStreamIngestionAction 使用，直接获取底层 JSON 流。
   *
   * @param fileId 文件服务返回的统一文件标识
   * @return 文件的输入流
   */
  InputStream downloadStream(FileId fileId);

  /**
   * 3. 获取文件上传的临时凭证 (Token/Presigned URL)
   * <p>
   * 供 BFF 层或应用层申请，直接返回给前端用于直传给文件服务，避免文件流经过业务服务。
   */
  String applyUploadToken(String clientIp, String userId, long maxSize);
}
