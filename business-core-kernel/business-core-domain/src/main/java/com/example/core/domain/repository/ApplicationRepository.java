package com.example.core.domain.repository;

import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.ApplicationId;

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
}
