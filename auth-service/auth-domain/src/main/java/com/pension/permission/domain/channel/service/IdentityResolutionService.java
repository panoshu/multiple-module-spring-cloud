package com.pension.permission.domain.channel.service;



import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.credential.repository.CredentialRepository;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.domain.product.PlanSnapshot;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.domain.user.aggregate.UserAggregate;
import com.pension.permission.domain.user.repository.UserRepository;
import com.pension.permission.domain.user.service.CredentialAuthenticator;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * 身份解析的唯一入口：给一个"谁声称自己被授权"(CredentialOwner) + 渠道 + proof
 * (+客户/计划级凭证还需要手机号)，解析出最终应该采信的AccountId。
 * <p>
 * 网上渠道主登录、总部登录、网点柜员登录、网点二次授权——四个场景全部收敛到这一个方法：
 * - 账号级持有者(密码/发给个人的UKey)：校验通过就是账号本身
 * - 客户/计划级持有者(企业/计划统一UKey)：校验通过只证明"这次操作被该客户/计划授权"，
 * 还要靠手机号定位到具体经办，并校验这个人确实跟该客户/计划有生效的身份分配，
 * 否则任何人报一个手机号就能冒充
 * <p>
 * 调用方(SessionApplicationService建会话 / SecondaryAuthService做身份提升)只是
 * 把这里返回的AccountId包装成不同的产物，本身不重复任何校验逻辑。
 */
@DomainService
@RequiredArgsConstructor
public final class IdentityResolutionService {

  private final CredentialRepository credentialRepository;
  private final CredentialAuthenticator credentialAuthenticator;
  private final UserRepository accountRepository;
  private final AssignmentRepository assignmentRepository;
  private final ProductGateway orgDirectory;

  public Optional<UserNo> resolve(CredentialOwner owner, AnnuityChannel channel, String proof, Mobile mobile) {
    boolean verified = credentialRepository.findByOwner(owner).stream()
      .filter(c -> c.applicableChannels().contains(channel))
      .anyMatch(c -> credentialAuthenticator.authenticate(c, proof));
    if (!verified) {
      return Optional.empty();
    }

    Optional<UserNo> accountId = (owner instanceof UserCredentialOwner(UserNo userNo))
      ? Optional.of(userNo)
      : locateByPhone(mobile, owner);

    return accountId
      .flatMap(accountRepository::load)
      .filter(UserAggregate::isActive) // 账号被冻结就不能建立新会话/新的身份提升，即使凭证本身还生效
      .map(UserAggregate::id);
  }

  private Optional<UserNo> locateByPhone(Mobile mobile, CredentialOwner owner) {
      return accountRepository.findByMobile(mobile)
      .map(UserAggregate::id)
      .filter(accountId -> belongsToOwner(accountId, owner));
  }

  private boolean belongsToOwner(UserNo accountId, CredentialOwner owner) {
    for (AgentIdentityAssignment assignment : assignmentRepository.findActiveByAccount(accountId)) {
      if (matches(assignment, owner)) {
        return true;
      }
    }
    return false;
  }

  private boolean matches(AgentIdentityAssignment assignment, CredentialOwner owner) {
    if (owner instanceof CustomerCredentialOwner(CustomerNo customerNo)) {
      return matchesCustomer(assignment, customerNo);
    }
    if (owner instanceof PlanCredentialOwner(PlanNo planNo)) {
      return matchesPlan(assignment, planNo);
    }
    return false;
  }

  private boolean matchesCustomer(AgentIdentityAssignment assignment, CustomerNo customerId) {
    return switch (assignment.scopeDimension()) {
      case CUSTOMER -> {
        if (assignment.scopeValue().equals(customerId.value())) {
          yield true;
        }
        yield assignment.isInheritable() && orgDirectory.descendantsOf(new CustomerNo(assignment.scopeValue()))
          .stream().anyMatch(descendant -> descendant.equals(customerId));
      }
      case PLAN -> orgDirectory.findPlan(new PlanNo(assignment.scopeValue()))
        .map(plan -> plan.customerNo().equals(customerId))
        .orElse(false);
      case PRODUCT -> false; // 产品维度的分配跟"某个具体客户"没有直接对应关系
      case GLOBAL -> false;
    };
  }

  private boolean matchesPlan(AgentIdentityAssignment assignment, PlanNo planId) {
    Optional<PlanSnapshot> planOpt = orgDirectory.findPlan(planId);
    if (planOpt.isEmpty()) {
      return false;
    }
    PlanSnapshot plan = planOpt.get();
    return switch (assignment.scopeDimension()) {
      case PLAN -> assignment.scopeValue().equals(planId.value());
      case PRODUCT -> assignment.scopeValue().equals(plan.productNo().value());
      case CUSTOMER -> {
        if (assignment.scopeValue().equals(plan.customerNo().value())) {
          yield true;
        }
        yield assignment.isInheritable() && orgDirectory.ancestorsOf(plan.customerNo())
          .stream().anyMatch(ancestor -> ancestor.value().equals(assignment.scopeValue()));
      }
      case GLOBAL -> false;
    };
  }
}
