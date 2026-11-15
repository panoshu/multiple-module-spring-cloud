package com.example.core.domain.business.gateway;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.types.ApplicationId;
import com.example.file.types.FileId;
import com.example.shared.exception.DomainException;

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

  /** 按解析文件凭证反向定位申请单，供文件解析回调关联使用。 */
  Optional<ApplicationId> findByFileId(FileId jsonFileId);

  /** 读取并强制要求存在，缺失时按领域规则拒绝（替代聚合根的 requireFileForParsing）。 */
  default FileId require(ApplicationId applicationId) {
    return load(applicationId).orElseThrow(() ->
      new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("Application: " + applicationId.value() + " 申请单尚未挂载解析文件"));
  }
}
