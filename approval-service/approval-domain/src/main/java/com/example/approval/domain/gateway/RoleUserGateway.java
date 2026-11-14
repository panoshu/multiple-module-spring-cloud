package com.example.approval.domain.gateway;

import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;

import java.util.List;
import java.util.Map;

/**
 * 角色用户网关接口（防腐层）
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
   * 获取同一计划下的用户列表（排除指定用户）。
   * <p>
   * 当前全仓无消费方（SAME_PLAN / LEVEL_UP 节点解析仍是 TODO），保留为未接线 stub；
   * 待组织目录接入与 SAME_PLAN 节点立专项时再实现。
   *
   * @param planId      计划ID
   * @param excludeUser 要排除的用户
   * @return 用户列表（当前为空）
   */
  List<UserNo> getUsersInSamePlan(PlanNo planId, UserNo excludeUser);
}
