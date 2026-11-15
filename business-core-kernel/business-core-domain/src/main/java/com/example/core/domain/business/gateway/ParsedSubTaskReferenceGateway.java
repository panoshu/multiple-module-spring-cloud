package com.example.core.domain.business.gateway;

import com.example.core.types.ApplicationId;

import java.util.Optional;

/**
 * 解析结果子任务引用网关（file-service canonical 数据消费凭证）。
 *
 * <p>当前 file-service 以 {@code subTaskId} 作为标准数据的分页拉取凭证
 * （{@code FileIntegrationGateway.fetchParsedRows(subTaskId, ...)}），
 * 不再物化独立 JSON 文件。kernel 只定义按 applicationId 关联的契约，
 * 具体存储在业务服务自有实现中。</p>
 *
 * @author business-core-kernel
 */
public interface ParsedSubTaskReferenceGateway {

  void save(ApplicationId applicationId, String subTaskId);

  Optional<String> load(ApplicationId applicationId);
}
