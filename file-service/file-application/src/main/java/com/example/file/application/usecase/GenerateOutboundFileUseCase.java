package com.example.file.application.usecase;

import com.example.file.application.command.GenerateOutboundFileCommand;
import com.example.file.domain.event.OutboundFileGeneratedEvent;
import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.gateway.FileStorageGateway;
import com.example.file.domain.gateway.StorageTargetResolver;
import com.example.file.domain.gateway.StoreResult;
import com.example.file.domain.gateway.TargetTemplateStorage;
import com.example.file.domain.model.storage.aggregate.FileMetadata;
import com.example.file.domain.model.parse.aggregate.SubTaskData;
import com.example.file.domain.model.storage.valueobject.FileUsage;
import com.example.file.domain.model.parse.valueobject.CanonicalData;
import com.example.file.domain.model.parse.valueobject.SplitUnit;
import com.example.file.domain.model.parse.valueobject.SubTaskSummary;
import com.example.file.domain.repository.FileMetadataRepository;
import com.example.file.domain.repository.SubTaskDataRepository;
import com.example.file.types.FileTaskId;
import com.example.shared.exception.BusinessException;
import com.example.shared.identifier.contract.IdService;
import com.example.core.types.BatchId;
import com.example.file.types.FileId;
import com.example.core.types.UserNo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 出站文件生成用例。
 * <p>
 * 核心流程：加载标准数据 → 合并补充数据 → Fesod 模板填充 → 存储到文件系统 → 发布领域事件。
 * 出站粒度为 1:N（每个 SubTaskData 生成一个出站文件）。
 * <p>
 * 文件存储流程：创建 FileMetadata(PENDING_UPLOAD) → store → markUploaded → save(发布事件)
 */
@Service
@Slf4j
public class GenerateOutboundFileUseCase {

  private static final String SUPPLEMENTAL_DATA_PREFIX = "biz_";

  private final IdService idService;
  private final SubTaskDataRepository subTaskRepository;
  private final FileMetadataRepository fileMetadataRepository;
  private final com.example.file.domain.gateway.OutboundEnricherGateway enricherGateway;
  private final com.example.file.domain.repository.TemplateConfigRepository configRepository;
  private final TargetTemplateStorage targetTemplateStorage;
  private final ExcelExporter excelExporter;
  private final FileStorageGateway fileStorageGateway;
  private final StorageTargetResolver targetResolver;

  public GenerateOutboundFileUseCase(IdService idService, SubTaskDataRepository subTaskRepository,
                                     FileMetadataRepository fileMetadataRepository,
                                     com.example.file.domain.gateway.OutboundEnricherGateway enricherGateway,
                                     com.example.file.domain.repository.TemplateConfigRepository configRepository,
                                     TargetTemplateStorage targetTemplateStorage,
                                     ExcelExporter excelExporter,
                                     FileStorageGateway fileStorageGateway,
                                     StorageTargetResolver targetResolver) {
    this.idService = idService;
    this.subTaskRepository = subTaskRepository;
    this.fileMetadataRepository = fileMetadataRepository;
    this.enricherGateway = enricherGateway;
    this.configRepository = configRepository;
    this.targetTemplateStorage = targetTemplateStorage;
    this.excelExporter = excelExporter;
    this.fileStorageGateway = fileStorageGateway;
    this.targetResolver = targetResolver;
  }

  @Transactional
  public OutboundFileGeneratedEvent execute(GenerateOutboundFileCommand cmd) {
    FileTaskId fileTaskId = FileTaskId.of(cmd.fileTaskId());
    Map<String, Object> supplementalData = cmd.supplementalData() != null
      ? cmd.supplementalData() : Map.of();

    validateSupplementalData(supplementalData);

    // enricher 端点（批次5 §7）：按目标模板配置解析（空=无增强直出）
    String enrichmentEndpoint = configRepository
      .findById(com.example.file.types.TemplateConfigId.of(cmd.targetTemplateId()))
      .map(com.example.file.domain.model.parse.aggregate.TemplateConfig::enrichmentEndpoint)
      .orElse(null);

    List<SubTaskSummary> summaries = subTaskRepository.findSummariesByTask(fileTaskId);
    if (summaries.isEmpty()) {
      throw new BusinessException(FileErrorCodes.SUB_TASK_NOT_FOUND)
        .withLogDetail("fileTaskId=" + cmd.fileTaskId());
    }

    List<OutboundFileGeneratedEvent.GeneratedFileInfo> generatedFiles = new ArrayList<>();
    UserNo operator = UserNo.of(cmd.operator());

    try (InputStream templateStream = targetTemplateStorage.load(cmd.targetTemplateId())) {
      for (SubTaskSummary summary : summaries) {
        SubTaskData subTask = subTaskRepository.findById(summary.subTaskId())
          .orElseThrow(() -> new BusinessException(FileErrorCodes.SUB_TASK_NOT_FOUND)
            .withLogDetail("subTaskId=" + summary.subTaskId()));

        OutboundFileGeneratedEvent.GeneratedFileInfo info =
          generateSingleFile(subTask, supplementalData, templateStream, cmd, operator, enrichmentEndpoint);
        generatedFiles.add(info);
      }
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      throw new BusinessException(FileErrorCodes.OUTBOUND_GENERATION_FAILED, e)
        .withLogDetail("fileTaskId=" + cmd.fileTaskId());
    }

    return OutboundFileGeneratedEvent.of(fileTaskId, generatedFiles, cmd.targetTemplateId());
  }

  private void validateSupplementalData(Map<String, Object> supplementalData) {
    for (String key : supplementalData.keySet()) {
      if (!key.startsWith(SUPPLEMENTAL_DATA_PREFIX)) {
        throw new BusinessException(FileErrorCodes.SUPPLEMENTAL_DATA_PREFIX_INVALID)
          .withUserDetail("补充数据字段 " + key + " 必须使用biz_前缀");
      }
    }
  }

  private OutboundFileGeneratedEvent.GeneratedFileInfo generateSingleFile(
    SubTaskData subTask, Map<String, Object> supplementalData,
    InputStream templateStream, GenerateOutboundFileCommand cmd, UserNo operator,
    String enrichmentEndpoint) {

    // 0. 导出数据增强（批次5 §7：模板填充前回调业务 enricher——fail-fast 容错已定稿）
    Map<String, Object> enrichedProps = enrichProps(subTask, cmd, enrichmentEndpoint);
    Map<String, Object> effectiveSupplemental = enrichedProps.isEmpty()
      ? supplementalData
      : java.util.stream.Stream.concat(supplementalData.entrySet().stream(),
          enrichedProps.entrySet().stream())
        .collect(java.util.stream.Collectors.toMap(
          java.util.Map.Entry::getKey, java.util.Map.Entry::getValue, (a, b) -> b,
          java.util.LinkedHashMap::new));

    // 1. 合并标准数据与补充数据
    CanonicalData merged = mergeSupplementalData(subTask, effectiveSupplemental);
    SplitUnit unit = buildSplitUnit(subTask, merged);

    // 2. Fesod 模板填充
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    excelExporter.export(unit, templateStream, baos);

    // 3. 创建 FileMetadata → 存储 → markUploaded
    FileId outboundFileId = idService.nextId(FileId.class);
    String originalName = buildOutboundFileName(subTask, cmd.targetTemplateId());

    var target = targetResolver.resolveByUsage(FileUsage.ARCHIVE, subTask.bizType().value());
    FileMetadata fileMeta = FileMetadata.create(
      outboundFileId,
      originalName,
      baos.size(),
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      FileUsage.ARCHIVE,
      subTask.bizType().value(),
      "file-service",
      new BatchId(cmd.fileTaskId()),
      target.targetId(),
      target.type(),
      operator,
      LocalDateTime.now().plusDays(90)
    );
    fileMetadataRepository.save(fileMeta);

    // 4. 存储文件流
    ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
    StoreResult result = fileStorageGateway.store(outboundFileId, bais, baos.size());

    // 5. 标记上传完成
    fileMeta = fileMetadataRepository.loadOrThrow(outboundFileId);
    fileMeta.markUploaded(result.storageKey(), result.digest());
    fileMetadataRepository.save(fileMeta);

    log.info("出站文件生成成功: subTaskId={}, fileId={}", subTask.id(), outboundFileId);
    return new OutboundFileGeneratedEvent.GeneratedFileInfo(
      subTask.id().value(), subTask.splitKeyValue(), outboundFileId.value());
  }

  private CanonicalData mergeSupplementalData(SubTaskData subTask, Map<String, Object> supplementalData) {
    CanonicalData merged = CanonicalData.of(subTask.properties(), subTask.tables());
    supplementalData.forEach(merged::setProperty);
    return merged;
  }

  private SplitUnit buildSplitUnit(SubTaskData subTask, CanonicalData merged) {
    var dataMap = new LinkedHashMap<String, Object>(merged.properties());
    merged.tables().forEach(dataMap::put);
    return new SplitUnit(subTask.splitKeyValue(), dataMap);
  }

  private String buildOutboundFileName(SubTaskData subTask, String targetTemplateId) {
    return targetTemplateId + "_" + subTask.splitKeyValue() + ".xlsx";
  }

  /**
   * 增强调用：subTask 的主表 base 行 → enricher 回调（按行合并为 properties 形态）.
   */
  private Map<String, Object> enrichProps(SubTaskData subTask, GenerateOutboundFileCommand cmd,
                                          String enrichmentEndpoint) {
    if (enrichmentEndpoint == null) {
      return Map.of();
    }
    // 主表第一行作为增强基准行（当前导出模型=单行属性+明细表；
    // 明细行级增强的演进留字段域配置模型落地后）
    var mainRows = subTask.tables().values().stream().findFirst()
      .map(rows -> rows.isEmpty() ? null : rows.get(0))
      .orElse(null);
    if (mainRows == null) {
      return Map.of();
    }
    var enriched = enricherGateway.invoke(enrichmentEndpoint,
      subTask.id().value(), cmd.fileTaskId(), java.util.List.of(mainRows));
    return enriched.isEmpty() ? Map.of() : enriched.get(0);
  }
}

