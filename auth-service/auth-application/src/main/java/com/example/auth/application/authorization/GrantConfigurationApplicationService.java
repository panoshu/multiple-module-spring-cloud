package com.example.auth.application.authorization;


import com.example.shared.domain.event.EventBus;
import com.example.auth.domain.authorization.aggregate.Grant;
import com.example.auth.domain.authorization.repository.GrantRepository;
import com.example.auth.domain.authorization.service.GrantConfigurationFactory;
import com.example.auth.types.GrantId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 能力层配置 + 总部主体授权/DENY例外 的用例编排入口
 */
@Service
@RequiredArgsConstructor
public class GrantConfigurationApplicationService {

  private final GrantConfigurationFactory grantConfigurationFactory;
  private final GrantRepository grantRepository;
  private final EventBus eventBus;

  @Transactional
  public GrantId createCapabilityGrant(CreateCapabilityGrantCommand command) {
    Grant grant = grantConfigurationFactory.createCapabilityGrant(
      command.scopeRules(), command.businesses(), command.effect(), command.createdBy());
    grantRepository.save(grant);
    grant.domainEvents().forEach(eventBus::publish);
    return grant.id();
  }

  @Transactional
  public GrantId createSubjectGrant(CreateHqSubjectGrantCommand command) {
    Grant grant = grantConfigurationFactory.createSubjectGrant(
      command.accountIds(), command.scopeRules(), command.permissions(),
      command.effect(), command.createdBy());
    grantRepository.save(grant);
    grant.domainEvents().forEach(eventBus::publish);
    return grant.id();
  }
}
