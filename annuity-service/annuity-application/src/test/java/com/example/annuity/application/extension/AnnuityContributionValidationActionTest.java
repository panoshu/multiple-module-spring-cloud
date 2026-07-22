package com.example.annuity.application.extension;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.service.AnnuityContributionRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnnuityContributionValidationAction")
class AnnuityContributionValidationActionTest {

  @Mock private AnnuityExtensionResolver extensionResolver;
  @Mock private AnnuityContributionRule contributionRule;
  @InjectMocks private AnnuityContributionValidationAction action;

  @Test
  @DisplayName("规则校验通过 - 返回 success")
  void execute_rulePassesReturnsSuccess() {
    BusinessApplication app = org.mockito.Mockito.mock(BusinessApplication.class);
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    );
    when(extensionResolver.resolve(app)).thenReturn(ext);
    when(contributionRule.validate(ext)).thenReturn(Optional.empty());

    ExtensionExecutionResult result = action.execute(app, null, java.util.Map.of());

    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  @DisplayName("规则校验失败 - 返回 failure")
  void execute_ruleFailsReturnsFailure() {
    BusinessApplication app = org.mockito.Mockito.mock(BusinessApplication.class);
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", -100L, false
    );
    when(extensionResolver.resolve(app)).thenReturn(ext);
    when(contributionRule.validate(ext)).thenReturn(Optional.of("缴费金额不能为负"));

    ExtensionExecutionResult result = action.execute(app, null, java.util.Map.of());

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.errorCode()).isEqualTo("INVALID_CONTRIBUTION");
  }
}
