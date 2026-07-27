package com.example.iam.application.service;

import com.example.iam.api.command.CreatePlanDelegationCommand;
import com.example.iam.api.command.CreatePlanDelegationCommand.DelegationPermissionItem;
import com.example.iam.api.command.RevokePlanDelegationCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PlanDelegationDTO;
import com.example.iam.api.query.GetPlanDelegationDetailQuery;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.domain.authorization.aggregate.root.PlanDelegation;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationPermission;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.repository.PlanDelegationRepository;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlanDelegationAppService} 单元测试。
 *
 * <p>覆盖计划代办关系的创建、撤销、详情查询等核心流程,验证唯一性校验、
 * 缓存按计划失效等关键协作。
 *
 * @author iam-service
 */
@DisplayName("计划代办关系管理应用服务测试")
@ExtendWith(MockitoExtension.class)
class PlanDelegationAppServiceTest {

  private static final Long DELEGATION_ID_VALUE = 3001L;
  private static final String DELEGATION_CODE = "DLG_001";
  private static final String DELEGATOR_PLAN_NO = "PLAN_A";
  private static final String DELEGATEE_PLAN_NO = "PLAN_B";
  private static final String BUSINESS_CODE = "ANNUITY_ESTABLISH";
  private static final String OPERATOR = "admin";
  private static final String REVOKE_REASON = "代办关系终止";

  @Mock private PlanDelegationRepository delegationRepository;
  @Mock private PermissionCachePort permissionCachePort;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;

  @InjectMocks
  private PlanDelegationAppService planDelegationAppService;

  @Nested
  @DisplayName("create 创建代办关系")
  class CreateTest {

    @Test
    @DisplayName("创建全部操作员代办关系:生成 ID、保存并失效双方计划缓存")
    void should_create_all_operators_delegation() {
      CreatePlanDelegationCommand command = buildCommand(DelegationType.ALL_OPERATORS, Set.of());
      when(delegationRepository.existsByDelegationCode(DELEGATION_CODE)).thenReturn(false);
      when(idService.nextLongId(PlanDelegationId.class, "IAM_PLAN_DELEGATION"))
          .thenReturn(PlanDelegationId.of(DELEGATION_ID_VALUE));

      IdResponseDTO response = planDelegationAppService.create(command);

      assertThat(response.id()).isEqualTo(DELEGATION_ID_VALUE);
      verify(delegationRepository).save(any(PlanDelegation.class));
      verify(permissionCachePort).evictByPlan(DELEGATOR_PLAN_NO);
      verify(permissionCachePort).evictByPlan(DELEGATEE_PLAN_NO);
      verify(eventBus, times(2)).publish(any());
    }

    @Test
    @DisplayName("代办编码已存在时抛业务异常,不生成 ID 不保存")
    void should_throw_when_delegation_code_duplicate() {
      CreatePlanDelegationCommand command = buildCommand(DelegationType.ALL_OPERATORS, Set.of());
      when(delegationRepository.existsByDelegationCode(DELEGATION_CODE)).thenReturn(true);

      assertThatThrownBy(() -> planDelegationAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(idService, never()).nextLongId(any(), any());
      verify(delegationRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("revoke 撤销代办关系")
  class RevokeTest {

    @Test
    @DisplayName("撤销活动代办关系:状态转为 REVOKED、保存并失效双方计划缓存")
    void should_revoke_active_delegation() {
      PlanDelegation delegation = buildDelegation();
      when(delegationRepository.load(PlanDelegationId.of(DELEGATION_ID_VALUE)))
          .thenReturn(Optional.of(delegation));
      RevokePlanDelegationCommand command = new RevokePlanDelegationCommand(
          DELEGATION_ID_VALUE, REVOKE_REASON, OPERATOR);

      planDelegationAppService.revoke(command);

      assertThat(delegation.status()).isEqualTo(DelegationStatus.REVOKED);
      verify(delegationRepository).save(delegation);
      verify(permissionCachePort).evictByPlan(DELEGATOR_PLAN_NO);
      verify(permissionCachePort).evictByPlan(DELEGATEE_PLAN_NO);
    }

    @Test
    @DisplayName("代办关系不存在时抛业务异常,不执行保存")
    void should_throw_when_delegation_not_found() {
      when(delegationRepository.load(PlanDelegationId.of(DELEGATION_ID_VALUE)))
          .thenReturn(Optional.empty());
      RevokePlanDelegationCommand command = new RevokePlanDelegationCommand(
          DELEGATION_ID_VALUE, REVOKE_REASON, OPERATOR);

      assertThatThrownBy(() -> planDelegationAppService.revoke(command))
          .isInstanceOf(BusinessException.class);

      verify(delegationRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("getDetail 查询代办关系详情")
  class GetDetailTest {

    @Test
    @DisplayName("查询存在的代办关系返回对应 DTO")
    void should_return_dto_when_delegation_exists() {
      PlanDelegation delegation = buildDelegation();
      when(delegationRepository.load(PlanDelegationId.of(DELEGATION_ID_VALUE)))
          .thenReturn(Optional.of(delegation));

      PlanDelegationDTO dto = planDelegationAppService.getDetail(
          new GetPlanDelegationDetailQuery(DELEGATION_ID_VALUE));

      assertThat(dto).isNotNull();
      assertThat(dto.delegationId()).isEqualTo(DELEGATION_ID_VALUE);
      assertThat(dto.delegationCode()).isEqualTo(DELEGATION_CODE);
      assertThat(dto.delegatorPlanNo()).isEqualTo(DELEGATOR_PLAN_NO);
      assertThat(dto.delegateePlanNo()).isEqualTo(DELEGATEE_PLAN_NO);
      assertThat(dto.delegationType()).isEqualTo(DelegationType.ALL_OPERATORS.name());
    }
  }

  private CreatePlanDelegationCommand buildCommand(DelegationType type, Set<Long> operators) {
    return new CreatePlanDelegationCommand(
        DELEGATION_CODE, DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
        type.name(), operators,
        Set.of(new DelegationPermissionItem(BUSINESS_CODE, Set.of(Action.HANDLE.name()))),
        null, null, OPERATOR);
  }

  private PlanDelegation buildDelegation() {
    return PlanDelegation.create(
        PlanDelegationId.of(DELEGATION_ID_VALUE), DELEGATION_CODE,
        DELEGATOR_PLAN_NO, DELEGATEE_PLAN_NO,
        DelegationType.ALL_OPERATORS, Set.of(),
        Set.of(DelegationPermission.of(BusinessCode.of(BUSINESS_CODE), Action.HANDLE)),
        LocalDateTime.now(), null,
        UserNo.of(OPERATOR));
  }
}
