package com.example.core.application.handler;

import com.example.core.domain.gateway.BusinessConfigGateway;
import com.example.core.domain.gateway.ConditionEvaluationGateway;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.MaterialConditionContext;
import com.example.core.domain.aggregate.valueobject.MaterialItem;
import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.aggregate.valueobject.config.MaterialRuleConfig;
import com.example.core.domain.service.engine.MaterialRuleEngine;
import com.example.core.domain.spi.StepActionHandler;
import com.example.core.domain.aggregate.valueobject.business.BusinessLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用材料清单准备主处理器 (计划层核心实现)
 * <p>
 * <b>【架构设计与职责边界】</b>
 * <p>1. 本类属于核心编排域 (kernel)，仅负责计算和处理 {@link BusinessLevel#PLAN} (计划层/总体级别) 的材料。
 * <p>2. 本核心域<b>严禁</b>引入、持有任何具体业务线（如年金、保理）的明细层仓储 (DetailRepository)。
 * <p>
 * <b>【各业务线同学请注意：如何支持您业务专属的明细层材料逻辑？】</b>
 * <p>如果您当前的业务步骤不仅需要生成总体材料，还需要为底下的千万条明细（如员工、资产明细）生成人头材料：
 * <p>1. <b>不要修改本类代码</b>。
 * <p>2. 请在您<b>自己的业务模块</b>中，实现核心域提供的 SPI 接口 {@link com.example.core.domain.spi.StepExtensionAction}。
 * <p>3. 在您的实现类中：
 * <p>   a. 注入您业务特有的明细层 Repository（如 EmployeeDetailRepository）。
 * <p>   b. 通过 app.id() 捞出您的明细数据。
 * <p>   c. 注入核心域的 {@link MaterialRuleEngine} 和 {@link ConditionEvaluationGateway}。
 * <p>   d. 遍历明细，将明细转换为局部 Facts，调用引擎计算 {@link BusinessLevel#DETAIL} 级别的材料并挂载到明细聚合根。
 * <p>4. <b>配置闭环</b>：在配置中心（JSON 配置）的本步骤中，将您实现的 Bean 名字配置到 `postExtensions` (后置动作链) 中。
 * <p>   这样，当本通用处理器跑完计划层材料后，编排引擎会自动按序调用您的明细层扩展动作，实现完美解耦！
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 22:49
 */
@Slf4j
@Component
public class PlanMaterialPreparationHandler implements StepActionHandler {

  private final BusinessConfigGateway configGateway;
  private final MaterialRuleEngine materialRuleEngine;
  private final ConditionEvaluationGateway conditionEvaluator;

  public PlanMaterialPreparationHandler(BusinessConfigGateway configGateway,
                                        MaterialRuleEngine materialRuleEngine,
                                        ConditionEvaluationGateway conditionEvaluator) {
    this.configGateway = configGateway;
    this.materialRuleEngine = materialRuleEngine;
    this.conditionEvaluator = conditionEvaluator;
  }

  @Override
  public String handlerName() {
    return "planMaterialPreparationHandler";
  }

  @Override
  public StepExecutionStatus execute(BusinessApplication app, BusinessMetaContext context) {
    log.info("开始执行通用计划层材料清单计算, applicationId={}", app.id());

    // 1. 通过统一网关，从配置中心拉取当前业务上下文下的全量材料规则 (包含 PLAN 和 DETAIL)
    List<MaterialRuleConfig> allRules = configGateway.getMaterialRules(context);

    // 2. 核心域各司其职，只过滤出计划层 (PLAN) 级别的材料规则进行计算
    List<MaterialRuleConfig> planRules = allRules.stream()
      .filter(rule -> rule.businessLevel() == BusinessLevel.PLAN)
      .toList();

    if (!planRules.isEmpty()) {
      // 3. 构建宏观条件评估上下文：直接面向总体的 BusinessMetaContext
      // 此时 SpEL 表达式可直接写如：#customerNo == 'X' 或 #facts['hasForeignInvestment'] == true
      MaterialConditionContext conditionContext =
        ruleKey -> conditionEvaluator.evaluate(ruleKey, context);

      // 4. 驱动核心域轻量级材料规则引擎，解析出满足条件的真实材料清单
      List<MaterialItem> planMaterials = materialRuleEngine.resolve(planRules, conditionContext);

      // 5. 将计算出的计划层材料，调用聚合根的充血方法安全挂载
      app.assignMaterials(planMaterials);
      log.info("成功挂载计划层材料清单，数量: {}", planMaterials.size());
    }

    // 6. 返回 SUCCESS。主线程将继续向下推动，自动执行各业务配置在 postExtensions 中的明细层动作
    return StepExecutionStatus.SUCCESS;
  }
}
