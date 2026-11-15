package com.example.core.application.engine.spi;

import com.example.core.types.ApplicationId;

/**
 * 申请单孵化初始化扩展点.
 *
 * <p>kernel 在表单解析后裂变 {@code BusinessApplication} 时，只创建通用聚合；
 * 业务服务的扩展数据（如年金 planType/initialContribution）由业务模块实现本接口，
 * 在 {@code ApplicationSpawnedListener} 点火推进流程<b>之前</b>同步完成装配。</p>
 *
 * <p><b>约定</b>：初始化幂等；抛异常将阻止该申请单推进（由监听器记录 ERROR，
 * 等待人工补偿），避免业务步骤在缺少扩展数据时得到模棱两可的结果。</p>
 *
 * @author business-core-kernel
 */
@FunctionalInterface
public interface ApplicationSpawnInitializer {

  /**
   * 为新生申请单准备业务扩展数据。
   *
   * @param applicationId 新生申请单 ID（事务已提交，可安全加载）
   */
  void initialize(ApplicationId applicationId);
}
