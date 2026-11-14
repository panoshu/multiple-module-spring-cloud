package com.example.core.domain.business.gateway;

import com.example.core.types.ApplicationId;
import com.example.file.types.FileId;

import java.util.Optional;

/**
 * 表单解析结果文件引用网关（spec §6.2，批次 37c-1）。
 * <p>
 * 解析后的明细 JSON 文件凭证属于「单次外部任务关联」，不应作为核心聚合的长期字段。
 * kernel 只定义按 {@code applicationId} 关联的契约，具体存储由业务服务实现：
 * 表单裂变时保存引用，步骤摄入/派发时按需读取。
 *
 * @author core-kernel
 */
public interface ParsedFileReferenceGateway {

  void save(ApplicationId applicationId, FileId jsonFileId);

  Optional<FileId> load(ApplicationId applicationId);
}
