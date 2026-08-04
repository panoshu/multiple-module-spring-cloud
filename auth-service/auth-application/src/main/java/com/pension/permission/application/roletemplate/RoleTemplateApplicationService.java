package com.pension.permission.application.roletemplate;


import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.domain.role.repository.RoleTemplateRepository;
import com.pension.permission.types.RoleTemplateId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleTemplateApplicationService {

  private final RoleTemplateRepository roleTemplateRepository;
  private final IdService idService;
  private final EventBus eventBus;

  @Transactional
  public RoleTemplateId createRoleTemplate(CreateRoleTemplateCommand command) {
    RoleTemplate template = RoleTemplate.create(
      idService.nextId(RoleTemplateId.class),
      command.createdBy(),
      command.roleCode(),
      command.scopeDimension(),
      command.scopeValue(),
      command.permissions(),
      RoleTemplateStatus.EFFECTIVE
    );
    roleTemplateRepository.save(template);
    template.domainEvents().forEach(eventBus::publish);

    return template.id();
  }
}
