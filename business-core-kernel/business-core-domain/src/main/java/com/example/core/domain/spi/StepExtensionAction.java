package com.example.core.domain.spi;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;

import java.util.Map;

/**
 * 步骤内的管道扩展动作 (Step Pipeline Extension Action)
 * <p>
 * 此接口用于实现挂载在某个步骤前/后的微型拦截器或自定义扩展逻辑。
 * 它支持在同一个步骤内按 {@code order} 顺序执行多个扩展，形成责任链。
 *
 * <h3>常见实现分类：</h3>
 * <ul>
 * <li><b>VALIDATION (校验类)</b>：例如 {@code AgeCheckValidator}，校验不通过则返回 failure 阻断流程。</li>
 * <li><b>ENRICHMENT (数据丰富类)</b>：调用外部接口获取画像数据，通过 {@code ExtensionExecutionResult.mutations()} 将新数据塞回 Context，供后续动作使用。</li>
 * <li><b>NOTIFICATION (通知类)</b>：发送短信或站内信，通常在配置中标记为 {@code isAsync=true}。</li>
 * <li><b>AUDIT (审计类)</b>：记录业务操作日志。</li>
 * </ul>
 *
 * <h3>⚠️ 异步安全警告：</h3>
 * 当该动作在配置中被标记为 <b>异步 (isAsync = true)</b> 时，它将被放入子线程执行。<br>
 * 此时，<b>绝对不要修改 {@link BusinessApplication} 的状态</b>，也不要尝试进行数据库操作，因为主事务可能已经提交。
 * 异步动作应当只读取数据 (Read-Only) 或调用外部系统的发送接口。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 20:59
 */
public interface StepExtensionAction {
  /**
   * 获取扩展动作的全局唯一标识，对应配置中心 JSON 里的 preExtensions/postExtensions 中的 beanName
   */
  String actionName();

  /**
   * 执行扩展动作逻辑
   *
   * @param app     业务申请单聚合根 (同步模式下可改状态，异步模式下仅作只读使用)
   * @param context 业务全量上下文 (包含 SpEL 提取的 fact 数据)
   * @param params  从 JSON 配置中透传过来的自定义参数 (如：模板ID、定制化错误提示语)
   * @return 扩展动作执行结果，可向外传递错误码，或向上下文追加新数据 (mutations)
   */
  ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params);
}
