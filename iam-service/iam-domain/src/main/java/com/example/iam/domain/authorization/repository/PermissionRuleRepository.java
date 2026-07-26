package com.example.iam.domain.authorization.repository;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.types.PermissionRuleId;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 权限规则聚合根仓储接口。
 *
 * <p>定义权限规则的查询语义,实现位于 {@code iam-infrastructure} 层。
 * {@link #findEffectiveRulesForContext} 是权限计算核心查询,一次返回所有适用维度的活动规则。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface PermissionRuleRepository extends Repository<PermissionRule, PermissionRuleId> {

  /**
   * 按规则编码查找规则(用于唯一性校验)。
   *
   * @param ruleCode 规则编码
   * @return 规则(可能为空)
   */
  Optional<PermissionRule> findByRuleCode(String ruleCode);

  /**
   * 检查规则编码是否已存在(用于创建时唯一性校验)。
   *
   * @param ruleCode 规则编码
   * @return 存在返回 true
   */
  boolean existsByRuleCode(String ruleCode);

  /**
   * 查询指定主体维度和主体标识下的所有活动规则。
   *
   * <p>用于权限计算时按维度加载规则。返回 status=ACTIVE 且在生效时间区间内的规则。
   *
   * @param subjectType 主体维度类型
   * @param subjectId   主体标识
   * @return 活动规则列表(可能为空)
   */
  List<PermissionRule> findBySubject(SubjectType subjectType, String subjectId);

  /**
   * 一次查询所有维度的活动规则(权限计算核心查询)。
   *
   * <p>设计文档 6.6 节:通过复合查询一次性返回 customerNo/operationMode/productNo/planNo/accountManagerCode
   * 五个维度对应的所有活动规则,避免多次往返数据库。
   *
   * @param customerNo         客户编号
   * @param operationMode      运作模式
   * @param productNo          产品编号
   * @param planNo             计划编号
   * @param accountManagerCode 账管人编号
   * @return 所有匹配的活动规则(按优先级降序)
   */
  List<PermissionRule> findEffectiveRulesForContext(String customerNo,
                                                     String operationMode,
                                                     String productNo,
                                                     String planNo,
                                                     String accountManagerCode);

  /**
   * 按状态查询规则(管理后台使用)。
   *
   * @param status 规则状态
   * @return 规则列表
   */
  List<PermissionRule> findByStatus(RuleStatus status);
}
