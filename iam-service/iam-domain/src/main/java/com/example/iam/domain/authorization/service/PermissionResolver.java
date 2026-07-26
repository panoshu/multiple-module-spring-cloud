package com.example.iam.domain.authorization.service;

import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.types.UserId;

/**
 * 权限解析器 - 计算指定用户在指定计划上下文下的权限快照。
 *
 * <p>设计文档 3.7 节:本接口是权限计算流程的入口,协调防腐层、仓储与组合策略,
 * 输出 {@link PermissionSnapshot} 供调用方决定缓存策略。
 *
 * <p>调用时机:
 * <ul>
 *   <li>网点渠道在二次授权瞬间调用 {@code resolve} 冻结快照</li>
 *   <li>其他渠道在 sa-token {@code IamStpInterfaceImpl.getPermissionList} 缓存未命中时调用</li>
 * </ul>
 *
 * <p>计算流程:
 * <ol>
 *   <li>通过 {@link com.example.iam.domain.authorization.gateway.PlanMetadataGateway} 加载计划元数据</li>
 *   <li>通过 {@link com.example.iam.domain.authorization.repository.PermissionRuleRepository}
 *       加载所有适用维度的活动规则</li>
 *   <li>过滤规则生效状态({@code isEffectiveAt(now)})</li>
 *   <li>通过 {@link com.example.iam.domain.authorization.repository.PlanDelegationRepository}
 *       加载被授权方为当前计划的活动代办关系</li>
 *   <li>过滤代办关系生效状态与操作员授权</li>
 *   <li>合并代办权限码集合</li>
 *   <li>构建 {@link com.example.iam.domain.authorization.aggregate.valueobject.PermissionCombinationContext}</li>
 *   <li>调用 {@link PermissionCombinationStrategy#combine} 计算最终权限</li>
 *   <li>输出 {@link PermissionSnapshot}(含权限集合 + 计算时间戳)</li>
 * </ol>
 *
 * <p>本接口属于 {@code domain.service} 包,作为权限计算的领域服务接口。
 * 实现类 {@code DefaultPermissionResolver} 位于同包,通过构造函数注入依赖。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface PermissionResolver {

  /**
   * 计算指定用户在指定计划上下文下的权限快照。
   *
   * <p>本方法不缓存结果,每次调用都重新计算;缓存策略由调用方决定
   * (如 sa-token Token-Session 缓存或二次授权快照冻结)。
   *
   * @param userId 用户标识(三渠道统一)
   * @param planNo 计划编号(外部系统标识,iam-service 不定义 PlanId 类型)
   * @return 权限快照(非空,即使无权限也返回空集快照)
   * @throws com.example.shared.exception.BusinessException 计划不存在
   *         (错误码 {@code SERVICE.IAM.0180})
   */
  PermissionSnapshot resolve(UserId userId, String planNo);
}
