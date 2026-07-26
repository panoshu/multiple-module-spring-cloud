package com.example.iam.domain.authorization.repository;

import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 计划代办关系聚合根仓储接口。
 *
 * <p>定义代办关系的查询语义,实现位于 {@code iam-infrastructure} 层。
 * {@link #findEffectiveByDelegator} 与 {@link #findEffectiveByDelegatee} 是权限计算核心查询,
 * 用于加载某计划相关的代办授权。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface PlanDelegationRepository extends Repository<PlanDelegation, PlanDelegationId> {

  /**
   * 按代办编码查找代办关系(用于唯一性校验)。
   *
   * @param delegationCode 代办编码
   * @return 代办关系(可能为空)
   */
  Optional<PlanDelegation> findByDelegationCode(String delegationCode);

  /**
   * 检查代办编码是否已存在(用于创建时唯一性校验)。
   *
   * @param delegationCode 代办编码
   * @return 存在返回 true
   */
  boolean existsByDelegationCode(String delegationCode);

  /**
   * 查询某授权方计划下的所有活动代办关系。
   *
   * <p>用于权限计算:被授权方计划经办办理业务时,合并授权方计划授予的权限。
   *
   * @param delegatorPlanNo 授权方计划编号
   * @return 活动代办关系列表(可能为空)
   */
  List<PlanDelegation> findEffectiveByDelegator(String delegatorPlanNo);

  /**
   * 查询某被授权方计划下的所有活动代办关系。
   *
   * <p>用于权限计算:被授权方计划经办办理业务时,加载其可获得的代办授权。
   *
   * @param delegateePlanNo 被授权方计划编号
   * @return 活动代办关系列表(可能为空)
   */
  List<PlanDelegation> findEffectiveByDelegatee(String delegateePlanNo);

  /**
   * 按状态查询代办关系(管理后台使用)。
   *
   * @param status 代办状态
   * @return 代办关系列表
   */
  List<PlanDelegation> findByStatus(DelegationStatus status);

  /**
   * 查询所有已过期但状态仍为 ACTIVE 的代办关系(用于定时任务批量标记过期)。
   *
   * @return 已过期的活动代办关系列表
   */
  List<PlanDelegation> findExpiredActive();
}
