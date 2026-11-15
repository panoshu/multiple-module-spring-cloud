package com.example.annuity.domain.repository;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.types.FormId;

import java.util.Optional;

/**
 * 年金表单草稿扩展字段存储 SPI.
 *
 * <p>表单创建时（{@code AnnuityAppService.uploadForm}）用户已选择计划类型/初始缴费等
 * 年金专属字段，但 kernel 的 {@code BusinessForm} 只承载通用上下文。本存储按
 * {@code formId} 暂存这些字段，待表单解析裂变出申请单时由
 * {@code AnnuityApplicationSpawnInitializer} 复制到 {@code applicationId} 维度。</p>
 *
 * <p>示例服务使用进程内实现；真实业务服务应持久化（如 t_annuity_form_draft），
 * 以支持服务重启与多实例。</p>
 *
 * @author annuity-service
 */
public interface AnnuityFormDraftStore {

  void save(FormId formId, AnnuityApplicationExtension draft);

  Optional<AnnuityApplicationExtension> load(FormId formId);
}
