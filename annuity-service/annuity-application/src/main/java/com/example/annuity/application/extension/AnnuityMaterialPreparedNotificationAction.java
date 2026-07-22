package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.valueobject.NotificationType;
import com.example.annuity.domain.gateway.AnnuityNotificationGateway;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金材料就绪通知扩展动作(sideEffect)
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityMaterialPreparedNotificationAction")
@RequiredArgsConstructor
public class AnnuityMaterialPreparedNotificationAction implements StepExtensionAction {

  private final AnnuityNotificationGateway notificationGateway;

  @Override
  public String actionName() {
    return "annuityMaterialPreparedNotificationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    notificationGateway.notifyOperator(
        app.operatorInfo().operatorId(),
        NotificationType.MATERIAL_READY,
        "申请单 " + app.id().value() + " 的材料已准备完毕"
    );
    return ExtensionExecutionResult.success();
  }
}
