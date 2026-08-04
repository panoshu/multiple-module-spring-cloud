package com.pension.permission.sdk;

/**
 * 不依赖任何AOP框架的最简用法：直接在业务代码里调用require(...)，权限不足抛异常中断执行。
 * 给还没有/不想接AOP的团队一个开箱即用的选项；接了AOP的团队可以在切面实现里内部复用这个类。
 * <p>
 * fail-closed在这里统一收口：Permission服务不可达时，一律当作"拒绝"处理，
 * 不会因为服务临时故障就放行——金融合规场景下这个默认值不应该被业务代码覆盖。
 */
public final class PermissionGuard {

  private PermissionGuard() {
  }

  public static void require(PermissionClient client, String accountId, String planId,
                             String businessCode, String actionCode) {
    boolean allowed;
    try {
      allowed = client.checkPermission(accountId, planId, businessCode, actionCode);
    } catch (PermissionServiceUnavailableException e) {
      throw new PermissionDeniedException(accountId, planId, businessCode, actionCode);
    }
    if (!allowed) {
      throw new PermissionDeniedException(accountId, planId, businessCode, actionCode);
    }
  }
}
