package com.example.auth.application.authorization;


import com.example.shared.domain.event.EventBus;
import com.example.shared.security.permission.PermissionChangedEvent;
import com.example.auth.domain.authorization.aggregate.Grant;
import com.example.auth.domain.authorization.repository.GrantRepository;
import com.example.auth.types.GrantId;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * 所有Grant通用的生命周期操作：审批通过/驳回/撤销，跟这条Grant是怎么创建出来的
 * (能力层配置/主体授权/代办/跨企业委托/角色模板)无关，所以不挂在DelegationApplicationService下面。
 * revoke尤其关键——账号冻结联动、代办关系紧急下线等场景都要走这里，
 * 同步发布GrantRevoked事件驱动快照失效，不等TTL。
 */
@Service
@RequiredArgsConstructor
public class GrantLifecycleApplicationService {

  private final GrantRepository grantRepository;
  private final EventBus eventBus;
  private final ApplicationEventPublisher permissionInvalidationPublisher;

  @Transactional
  public void approve(ApproveGrantCommand command) {
    mutate(command.grantId(), grant -> grant.approve(command.operator()));
  }

  @Transactional
  public void reject(RejectGrantCommand command) {
    mutate(command.grantId(), grant -> grant.reject(command.operator()));
  }

  @Transactional
  public void revoke(RevokeGrantCommand command) {
    mutate(command.grantId(), grant -> grant.revoke(command.operator()));
  }

  private void mutate(GrantId grantId, Consumer<Grant> action) {
    Grant grant = grantRepository.load(grantId)
      .orElseThrow(() -> new IllegalArgumentException("Grant不存在: " + grantId.value()));
    action.accept(grant);
    grantRepository.save(grant);
    grant.domainEvents().forEach(eventBus::publish);

    // Phase D 失效接线：Grant 生效状态变化 → 广播缓存失效信号。
    // Grant 的 subject 可能是用户清单/计划全员/计划角色（GrantSubject 密封族），
    // 影响账号不可廉价枚举 → 保守取全局失效；TTL 300s 兜底，误伤仅为重新校验。
    permissionInvalidationPublisher.publishEvent(PermissionChangedEvent.global("auth-service"));
  }
}
