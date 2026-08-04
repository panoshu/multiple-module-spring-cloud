package com.example.core.domain.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.BatchId;

import java.util.List;
import java.util.Optional;

/**
 * 业务批次表单聚合根仓库
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 10:20
 */
public interface ApplicationRepository extends Repository<BusinessApplication, ApplicationId> {

  /**
   * 通过文件任务 ID 反查业务申请单。
   * <p>
   * 用于 file-service 解析完成后通过 {@code FileParsedEventDTO.fileTaskId} 回调时，
   * 定位到对应的业务申请单聚合根以推进流程。
   * <p>
   * <b>注意：</b>本方法为 {@code default} 实现，抛出 {@link UnsupportedOperationException}，
   * 具体业务服务（如 annuity-service）需按需覆写本方法以提供真实的反查能力。
   *
   * @param fileTaskId 文件任务 ID
   * @return 命中的业务申请单；未命中返回 {@link Optional#empty()}
   */
  default Optional<BusinessApplication> findByFileTaskId(String fileTaskId) {
    throw new UnsupportedOperationException(
      "ApplicationRepository.findByFileTaskId 尚未实现，具体业务服务需覆写本方法");
  }

  /**
   * 通过批次 ID 查询该批次下所有业务申请单。
   * <p>
   * 用于 {@code BusinessApplicationApi.list} 接口按批次聚合查询申请单列表。
   * <p>
   * <b>注意:</b>本方法为 {@code default} 实现,抛出 {@link UnsupportedOperationException},
   * 具体业务服务需按需覆写本方法以提供真实的查询能力。
   *
   * @param batchId 批次 ID
   * @return 该批次下的所有业务申请单列表;若未覆写则抛出异常
   */
  default List<BusinessApplication> findByBatchId(BatchId batchId) {
    throw new UnsupportedOperationException(
      "ApplicationRepository.findByBatchId 尚未实现,具体业务服务需覆写本方法");
  }
}
