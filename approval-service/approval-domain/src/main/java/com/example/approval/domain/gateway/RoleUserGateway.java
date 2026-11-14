package com.example.approval.domain.gateway;

import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;

import java.util.List;
import java.util.Map;

/**
 * 角色与计划层级用户网关接口（防腐层）
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public interface RoleUserGateway {

  /**
   * 按角色批量查询指定 PLAN 范围内的用户（批次 60，立项 §6 开放点 1/3）。
   * <p>
   * 现契约带 {@code planNo}（PLAN 精确 scope），避免不带 scope 时返回全平台同角色用户
   * 造成跨计划数据泄漏；批量入参规避消费侧逐实例展开的 N+1 调用。
   * auth 服务不可用或响应异常时实现应 fail-closed 返回空 Map，不回退到请求体身份。
   *
   * @param roleIds 角色编码列表
   * @param planNo  计划号（PLAN 精确 scope）
   * @return 角色编码 → 用户列表
   */
  Map<String, List<UserNo>> getUsersByRoles(List<String> roleIds, PlanNo planNo);

  /**
   * 获取同一计划下的用户列表并排除指定用户（批次 68 接线）。
   * <p>
   * 经 auth-api {@code PlanDirectoryApi} 的 {@code /members} 查询：服务端按 PLAN 精确
   * scope 与 CUSTOMER / PRODUCT 继承匹配，并排除 {@code excludeUser}（发起人本人）。
   * auth 不可用、响应异常或计划层级数据缺失时应 fail-closed 返回空列表，不回退节点
   * 配置的审批人，避免把他人任务误展开为 SAME_PLAN 审批人。
   *
   * @param planId      计划号
   * @param excludeUser 要排除的用户（发起人，可空）
   * @return 用户列表
   */
  List<UserNo> getUsersInSamePlan(PlanNo planId, UserNo excludeUser);

  /**
   * 获取上一级计划（直接父计划）下的用户列表并排除指定用户（批次 69 接线）。
   * <p>
   * 经 auth-api {@code PlanDirectoryApi} 的 {@code /parent} 取直接父计划号，再按
   * {@code /members} 取父计划成员。无父计划（到顶）、组织目录不可用、响应异常或
   * 计划层级数据缺失时应 fail-closed 返回空列表，不越级、不回退节点配置的审批人。
   * {@code TerminalLevel} 的多级追溯语义不在本立项范围（§6 决策 3）。
   *
   * @param planNo      当前计划号
   * @param excludeUser 要排除的用户（发起人，可空）
   * @return 父计划成员列表
   */
  List<UserNo> getUsersInParentPlan(PlanNo planNo, UserNo excludeUser);
}
