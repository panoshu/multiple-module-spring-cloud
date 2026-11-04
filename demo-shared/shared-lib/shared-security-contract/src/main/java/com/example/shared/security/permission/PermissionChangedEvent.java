package com.example.shared.security.permission;

/**
 * 权限变更集成事件（跨服务缓存失效信号，Phase D 失效链接线）.
 *
 * <p>spec 2026-11-03-auth-permission-redesign §5 失效链：
 * auth 权限变更 → 本事件 → 各业务服务 {@code PermissionCacheInvalidationListener}
 * → 清除 permission-starter 本地 Caffeine 缓存键。
 *
 * <ul>
 *   <li>{@code accountId} 为 {@code null} 表示<b>全局失效</b>（权限码表注册、角色模板变更）；
 *       非 null 表示定向失效该账号（授权审批/角色分配变更）</li>
 *   <li>{@code planId} 可空——为空时失效该账号全部计划维度条目</li>
 *   <li><b>演示环境</b>：Spring {@code @EventListener} 同进程直达（auth 进程内）；
 *       <b>生产环境</b>：经 shared-event-starter 事件投递器（Redis/RocketMQ）跨服务广播，
 *       与审批结果事件（ApprovalInstanceApprovedEventDTO 等）同模式</li>
 *   <li>TTL 兜底：即便事件丢失，本地缓存 300s 过期后自然重新校验（fail-safe）</li>
 * </ul>
 *
 * <p>契约层纪律：纯 record，零 Spring 传输注解，供 auth-api（发布方）与
 * shared-permission-starter（消费方）共同引用，依赖方向单向指向本模块。
 *
 * @author shared-security-contract
 */
public record PermissionChangedEvent(
  String accountId,
  String planId,
  String source,
  long changedAtMillis) {

  /**
   * 定向失效：指定账号（可选计划维度）.
   */
  public static PermissionChangedEvent forAccount(String accountId, String planId, String source) {
    return new PermissionChangedEvent(accountId, planId, source, System.currentTimeMillis());
  }

  /**
   * 全局失效：权限码表 / 角色模板类变更，影响面不可枚举.
   */
  public static PermissionChangedEvent global(String source) {
    return new PermissionChangedEvent(null, null, source, System.currentTimeMillis());
  }

  public boolean isGlobal() {
    return accountId == null;
  }
}
