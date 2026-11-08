package com.example.annuity.infrastructure.gateway;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.infrastructure.engine.event.IntegrationEventSimulator;
import com.example.file.api.event.FileParsedEventDTO;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import com.example.file.types.FileId;
import com.example.core.types.FormId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mock 文件集成网关（演示环境）
 * <p>
 * 替代 kernel 的 {@code FileServiceIntegrationGateway} 默认实现，避免真实调用 file-service。
 * 当流程触发异步解析时，本 Mock 立即通过 {@link IntegrationEventSimulator} 发布
 * {@link FileParsedEventDTO}，模拟 file-service 解析完成回调，从而驱动
 * {@code FileParsedEventListener} 推进业务流程。
 * <p>
 * 当流程触发出站文件生成时，本 Mock 立即发布 {@link OutboundFileGeneratedEventDTO}，
 * 模拟 file-service 出站文件生成完成回调，从而驱动
 * {@code OutboundFileEventListener} 推进业务流程。
 * <p>
 * <b>【@Primary 覆盖】</b>kernel 的 {@code FileServiceIntegrationGateway} 也是 {@code @Component}，
 * 通过 {@link Primary} 注解让本 Mock 在 Spring 容器中优先被注入，避免两个 Bean 冲突。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class MockFileIntegrationGateway implements FileIntegrationGateway {

  private static final String BIZ_TYPE_FORM_DETAIL = "FORM_DETAIL";
  private static final String STATUS_SUCCESS = "SUCCESS";

  private final IntegrationEventSimulator eventSimulator;

  /**
   * 触发异步解析（BusinessForm 重载）
   * <p>
   * 使用 {@code businessForm.id().value()} 作为 fileTaskId，立即发布解析完成事件。
   */
  @Override
  public void triggerAsyncParsing(BusinessForm businessForm, BusinessMetaContext businessMetaContext) {
    if (businessForm == null) {
      log.warn("BusinessForm 为空，跳过 Mock 解析");
      return;
    }
    String fileTaskId = businessForm.id().value();
    log.info("[Mock] 立即模拟文件解析完成, formId={}", fileTaskId);
    publishFileParsedEvent(fileTaskId);
  }

  /**
   * 触发异步解析（FormId + FileId 重载）
   * <p>
   * 使用 {@code sourceFileId.value()} 作为 fileTaskId，立即发布解析完成事件。
   * 测试场景中，业务申请单的 {@code parsedJsonFileId} 与 {@code sourceFileId} 一致，
   * 从而 {@code FileParsedEventListener} 能通过 {@code findByFileTaskId} 反查到申请单。
   */
  @Override
  public void triggerAsyncParsing(FormId formId, FileId sourceFileId, String parseTemplateId,
                                  Map<String, Object> splitRules) {
    String fileTaskId = sourceFileId != null ? sourceFileId.value() : formId.value();
    log.info("[Mock] 立即模拟文件解析完成, formId={}, fileTaskId={}", formId.value(), fileTaskId);
    publishFileParsedEvent(fileTaskId);
  }

  @Override
  public InputStream downloadStream(FileId fileId, BusinessMetaContext context) {
    log.info("[Mock] 返回模拟员工明细 JSON 流, fileId={}", fileId.value());
    String mockJson = """
      [
        {
          "employeeName": "张三",
          "idCardNo": "110101199001011234",
          "age": 35,
          "monthlySalary": 100000,
          "monthlyContribution": 12000
        },
        {
          "employeeName": "李四",
          "idCardNo": "110101198505056789",
          "age": 40,
          "monthlySalary": 150000,
          "monthlyContribution": 18000
        }
      ]
      """;
    return new java.io.ByteArrayInputStream(mockJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Override
  public String applyUploadToken(String clientIp, String userId, long maxSize) {
    return "mock-upload-token-" + UUID.randomUUID();
  }

  /**
   * 触发出站文件生成（Mock 实现）
   * <p>
   * 使用 {@code fileTaskId}(= applicationId) 作为关联键，立即发布生成完成事件。
   * Mock 返回一个生成的文件信息（subTaskId 和 outboundFileId 均为模拟值）。
   */
  @Override
  public void triggerOutboundGeneration(String fileTaskId, String targetTemplateId,
                                         Map<String, Object> supplementalData, String operator) {
    log.info("[Mock] 立即模拟出站文件生成完成, fileTaskId={}, targetTemplateId={}", fileTaskId, targetTemplateId);
    publishOutboundFileGeneratedEvent(fileTaskId, targetTemplateId);
  }

  /**
   * 构造并发布 FileParsedEventDTO 模拟 file-service 解析完成回调
   */
  private void publishFileParsedEvent(String fileTaskId) {
    FileParsedEventDTO event = new FileParsedEventDTO(
      UUID.randomUUID().toString(),
      fileTaskId,
      BIZ_TYPE_FORM_DETAIL,
      STATUS_SUCCESS,
      0,
      List.of(),
      null,
      LocalDateTime.now()
    );
    eventSimulator.publish(event);
    log.info("[Mock] 已发布 FileParsedEventDTO, fileTaskId={}, status={}", fileTaskId, STATUS_SUCCESS);
  }

  /**
   * 构造并发布 OutboundFileGeneratedEventDTO 模拟 file-service 出站文件生成完成回调
   */
  private void publishOutboundFileGeneratedEvent(String fileTaskId, String targetTemplateId) {
    OutboundFileGeneratedEventDTO event = new OutboundFileGeneratedEventDTO(
      UUID.randomUUID().toString(),
      fileTaskId,
      targetTemplateId,
      List.of(new OutboundFileGeneratedEventDTO.GeneratedFileInfoDTO(
        "mock-subtask-" + UUID.randomUUID(),
        "default-split-key",
        "mock-outbound-file-" + UUID.randomUUID()
      )),
      LocalDateTime.now()
    );
    eventSimulator.publish(event);
    log.info("[Mock] 已发布 OutboundFileGeneratedEventDTO, fileTaskId={}, targetTemplateId={}", fileTaskId, targetTemplateId);
  }

  /**
   * Mock 分页拉取（批次2 SPI 新方法）：按页返回 Mock 员工行.
   */
  @Override
  public ParsedRowsPage fetchParsedRows(String subTaskId, String tableCode, int page, int size) {
    Map<String, Object> row1 = new java.util.LinkedHashMap<>();
    row1.put("employeeName", "张三");
    row1.put("monthlySalary", 100000);
    Map<String, Object> row2 = new java.util.LinkedHashMap<>();
    row2.put("employeeName", "李四");
    row2.put("monthlySalary", 150000);
    List<Map<String, Object>> all = List.of(row1, row2);
    int from = Math.min((page - 1) * size, all.size());
    int to = Math.min(from + size, all.size());
    return new ParsedRowsPage(all.size(), from, to < all.size(), all.subList(from, to));
  }
}