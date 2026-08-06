package com.pension.permission.infrastructure.permission;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.event.GrantApproved;
import com.pension.permission.domain.authorization.event.GrantRejected;
import com.pension.permission.domain.authorization.event.GrantRevoked;
import com.pension.permission.domain.authorization.repository.GrantRepository;
import com.pension.permission.domain.authorization.valueobject.subject.CapabilitySubject;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanAllMembersSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanRoleSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.domain.permission.spi.PermissionCacheStore;
import com.pension.permission.types.GrantId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Grant 事件监听器 - 主动刷新权限缓存.
 *
 * <p>监听 {@link GrantApproved} / {@link GrantRevoked} 事件，主动清除受影响账号的
 * {@link com.pension.permission.domain.channel.valueobject.SessionPermissionCache}，
 * 避免业务感知到最长 TTL（默认 5 分钟）的权限延迟。</p>
 *
 * <h3>受影响账号的提取逻辑</h3>
 * <ul>
 *   <li>{@link UserListSubject}：精确失效指定账号集合（调用 {@code evictAll}）。</li>
 *   <li>{@link CapabilitySubject}：影响全局，当前无法精确失效，依赖 TTL 兜底。</li>
 *   <li>{@link PlanAllMembersSubject}：需通过 {@code PlanMembershipLookup} 反查计划成员，
 *       当前未注入此依赖，暂依赖 TTL 兜底。</li>
 *   <li>{@link PlanRoleSubject}：同上，需查询计划中具备指定角色的成员。</li>
 * </ul>
 *
 * <p>{@link GrantRejected} 不触发刷新：被拒绝的 Grant 从未生效，不影响现有权限。</p>
 *
 * <p>本期实现对 UserListSubject 做精确失效，其余 subject 类型依赖 TTL 兜底，
 * 后续迭代可注入 PlanMembershipLookup 扩展精确失效范围。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantEventRefreshListener {

  private final PermissionCacheStore permissionCacheStore;
  private final GrantRepository grantRepository;

  /**
   * Grant 批准事件 - 加载 Grant 并失效受影响账号缓存.
   *
   * @param event Grant 批准事件
   */
  @EventListener
  public void onGrantApproved(GrantApproved event) {
    log.info("Grant 批准事件触发缓存刷新: grantId={}", event.grantId());
    evictAffectedSessions(event.grantId());
  }

  /**
   * Grant 撤销事件 - 加载 Grant 并失效受影响账号缓存.
   *
   * @param event Grant 撤销事件
   */
  @EventListener
  public void onGrantRevoked(GrantRevoked event) {
    log.info("Grant 撤销事件触发缓存刷新: grantId={}", event.grantId());
    evictAffectedSessions(event.grantId());
  }

  /**
   * Grant 拒绝事件 - 不触发缓存刷新.
   *
   * <p>被拒绝的 Grant 从未生效，不会影响现有权限缓存。</p>
   *
   * @param event Grant 拒绝事件
   */
  @EventListener
  public void onGrantRejected(GrantRejected event) {
    log.debug("Grant 拒绝事件不触发缓存刷新: grantId={}", event.grantId());
  }

  /**
   * 通过 grantId 加载 Grant 聚合根，按 subject 类型分派缓存失效策略.
   *
   * @param grantId Grant ID
   */
  private void evictAffectedSessions(GrantId grantId) {
    Optional<Grant> grantOpt = grantRepository.load(grantId);
    if (grantOpt.isEmpty()) {
      log.warn("Grant 不存在，无法精确失效缓存，依赖 TTL 兜底: grantId={}", grantId);
      return;
    }

    Grant grant = grantOpt.get();
    GrantSubject subject = grant.subject();
    evictBySubject(grantId, subject);
  }

  /**
   * 按 subject 类型分派缓存失效策略.
   */
  private void evictBySubject(GrantId grantId, GrantSubject subject) {
    if (subject instanceof UserListSubject userList) {
      evictUserList(grantId, userList);
    } else if (subject instanceof CapabilitySubject) {
      log.warn("CapabilitySubject 影响全局，无法精确失效，依赖 TTL 兜底: grantId={}", grantId);
    } else if (subject instanceof PlanAllMembersSubject planAllMembers) {
      log.warn("PlanAllMembersSubject 需要查询计划成员，当前未实现精确失效，依赖 TTL 兜底: grantId={}, planNo={}",
        grantId, planAllMembers.planNo());
    } else if (subject instanceof PlanRoleSubject planRole) {
      log.warn("PlanRoleSubject 需要查询计划角色成员，当前未实现精确失效，依赖 TTL 兜底: grantId={}, planNo={}, roleCode={}",
        grantId, planRole.planNo(), planRole.roleCode());
    } else {
      log.warn("未知 subject 类型，依赖 TTL 兜底: grantId={}, subjectClass={}",
        grantId, subject.getClass().getName());
    }
  }

  /**
   * 失效 UserListSubject 指定账号集合的权限缓存.
   */
  private void evictUserList(GrantId grantId, UserListSubject userList) {
    Set<UserNo> accountIds = userList.accountIds();
    permissionCacheStore.evictAll(accountIds);
    log.info("已失效 UserListSubject 账号缓存: grantId={}, accountCount={}",
      grantId, accountIds.size());
  }
}
