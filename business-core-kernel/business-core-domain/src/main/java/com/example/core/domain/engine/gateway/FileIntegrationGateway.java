package com.example.core.domain.engine.gateway;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.file.types.FileId;
import com.example.core.types.FormId;

import java.io.InputStream;
import java.util.List;
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
   * 带外部协作幂等键的解析触发（spec §11.4）。默认委托旧签名；
   * kernel 基础设施实现覆盖本方法，把 {@code requestNo} 写入 {@code UploadFileRequest.clientRequestNo}。
   */
  default void triggerAsyncParsing(FormId formId, FileId sourceFileId, String parseTemplateId,
                                   Map<String, Object> splitRules, String requestNo) {
    triggerAsyncParsing(formId, sourceFileId, parseTemplateId, splitRules);
  }

  /**
   * 2. 流式下载文件 (防 OOM 大杀器)
   * <p>
   * 供应用层的 AbstractJsonStreamIngestionAction 使用，直接获取底层 JSON 流。
   * <p>
   * <b>【为何需要 context】</b>文件服务的下载需先申请下载令牌,令牌申请要求校验
   * 客户/产品/下载人一致性,这些信息由 {@link BusinessMetaContext} 提供。
   *
   * @param fileId  文件服务返回的统一文件标识
   * @param context 业务上下文(提供 customerNo/productNo 等令牌申请要素)
   * @return 文件的输入流
   */
  InputStream downloadStream(FileId fileId, BusinessMetaContext context);

  /**
   * 3. 获取文件上传的临时凭证 (Token/Presigned URL)
   * <p>
   * 供 BFF 层或应用层申请，直接返回给前端用于直传给文件服务，避免文件流经过业务服务。
   */
  String applyUploadToken(String clientIp, String userId, long maxSize);

  /**
   * 4. 触发出站文件生成
   * <p>
   * 将业务申请单关联的标准数据按目标模板填充为 Excel，由 file-service 异步生成并存储。
   * 完成后 file-service 发布 {@code OutboundFileGeneratedEventDTO}，由监听器推进流程。
   *
   * @param fileTaskId        入站解析时 file-service 返回的文件任务 ID（标准数据凭证）
   * @param targetTemplateId  目标模板 ID（对应 {@code TemplateConfig.targetTemplateRef}）
   * @param supplementalData  业务侧补充数据，字段必须以 {@code biz_} 为前缀
   * @param operator          操作人编号
   */
  void triggerOutboundGeneration(String fileTaskId, String targetTemplateId,
                                  Map<String, Object> supplementalData, String operator);

  /**
   * 带外部协作幂等键的出站生成触发（spec §11.4）。默认委托旧签名；
   * kernel 基础设施实现覆盖本方法，把 {@code requestNo} 写入 {@code GenerateOutboundFileRequest}。
   */
  default void triggerOutboundGeneration(String fileTaskId, String targetTemplateId,
                                          Map<String, Object> supplementalData, String operator,
                                          String requestNo) {
    triggerOutboundGeneration(fileTaskId, targetTemplateId, supplementalData, operator);
  }

  /**
   * 5. 分页拉取解析数据（数据消费闭环，2026-11-08 批次2）.
   *
   * <p>FileParsedEvent 消费方凭 subTaskId 凭证分页拉取 canonical 行数据——
   * 数据留在 file 域（数据不动原则），消费方低内存消费、游标续传.
   *
   * @param subTaskId 子任务凭证（事件载荷）
   * @param tableCode 数据表编码（单表场景可空——file 侧取默认表）
   */
  ParsedRowsPage fetchParsedRows(String subTaskId, String tableCode, int page, int size);

  /**
   * 分页结果（行数据为字段名→值的 Map，零 file 契约依赖）.
   */
  record ParsedRowsPage(
    int totalCount,
    int startPos,
    boolean hasMore,
    List<Map<String, Object>> rows
  ) {
  }
}
