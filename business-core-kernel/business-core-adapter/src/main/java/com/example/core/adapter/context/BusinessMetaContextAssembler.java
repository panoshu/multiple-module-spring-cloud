package com.example.core.adapter.context;

import com.example.core.api.context.BusinessMetaContext;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import org.springframework.stereotype.Component;

/**
 * 业务元数据上下文组装器
 *
 * <p>从前端 Command 的 {@code businessType} + {@code planNo} 与 {@link SessionContext}
 * 组装完整的 {@link BusinessMetaContext}。
 *
 * <p>校验 {@code commandPlanNo} 与 {@code session.planNo} 一致,防止跨计划办理。
 * 客户/产品/账管人等敏感字段完全来自 SessionContext,杜绝前端伪造。
 *
 * @author panoshu
 */
@Component
public class BusinessMetaContextAssembler {

  /**
   * 组装业务元数据上下文。
   *
   * @param businessType  业务类型(来自前端 Command)
   * @param commandPlanNo 计划编号(来自前端 Command,用于校验一致性)
   * @param session       会话上下文(来自 X-Session-Context header)
   * @return 完整的业务元数据上下文
   * @throws BusinessException 当 commandPlanNo 与 session.planNo 不一致时
   */
  public BusinessMetaContext assemble(String businessType, String commandPlanNo, SessionContext session) {
    if (!commandPlanNo.equals(session.planNo())) {
      throw new BusinessException(CommonError.BAD_REQUEST)
        .withUserDetail("所选计划与会话中的计划不一致")
        .withLogDetail("commandPlanNo=%s, sessionPlanNo=%s".formatted(commandPlanNo, session.planNo()));
    }
    return new BusinessMetaContext(
      businessType,
      session.planNo(),
      session.customerNo(),
      session.customerName(),
      session.productNo(),
      session.productName(),
      session.planName(),
      session.operationModel(),
      session.accountManager()
    );
  }
}
