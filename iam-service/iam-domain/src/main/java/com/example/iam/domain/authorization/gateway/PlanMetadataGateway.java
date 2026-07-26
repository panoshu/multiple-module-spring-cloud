package com.example.iam.domain.authorization.gateway;

import com.example.iam.domain.authorization.aggregate.valueobject.PlanMetadata;
import java.util.List;
import java.util.Optional;

/**
 * 计划元数据查询网关 - 防腐层接口,从外部业务系统加载计划基础信息。
 *
 * <p>设计文档 3.7 节 PermissionResolver 计算流程步骤 1 通过本网关获取计划上下文,
 * 提供权限规则匹配所需的客户/产品/运作模式/账管人维度。
 *
 * <p>实现位于 infrastructure 层(通过 Retrofit/HTTP 客户端调用外部计划服务),
 * 将外部 DTO 转换为领域值对象 {@link PlanMetadata}。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface PlanMetadataGateway {

  /**
   * 按计划编号查询计划元数据。
   *
   * @param planNo 计划编号(外部系统标识)
   * @return 计划元数据;不存在时返回 {@link Optional#empty()}
   */
  Optional<PlanMetadata> findByPlanNo(String planNo);

  /**
   * 查询某客户下所有可选计划。
   *
   * <p>用于用户选择计划上下文时展示可选列表。
   *
   * @param customerNo 客户编号
   * @return 可选计划元数据列表;无数据时返回空列表
   */
  List<PlanMetadata> findSelectablePlansByCustomer(String customerNo);
}
