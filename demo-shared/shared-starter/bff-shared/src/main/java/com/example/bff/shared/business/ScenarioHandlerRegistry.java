package com.example.bff.shared.business;

import com.example.bff.shared.errorcode.BffErrorCode;
import com.example.shared.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 场景处理器注册表 — 自动收集所有 {@link BusinessScenarioHandler} 实现
 *
 * <p>Spring 启动时注入所有 handler Bean，按 {@link BusinessScenarioHandler#supportedTypes()}
 * 中的每个业务类型建立查找表。一个 handler 可覆盖多个 businessType。
 * BFF 调度层通过 {@link #handlerFor(String)} 获取对应 handler 并委托执行。
 *
 * <p>新增业务场景只需:
 * <ol>
 *   <li>新增 {@code XxxScenarioHandler} 实现类（标注 {@code @Service}）</li>
 *   <li>BFF 引入对应 {@code xxx-api} 依赖</li>
 *   <li>YAML 加一行 httpexchange 配置</li>
 * </ol>
 * 不改动本类或任何已有代码。
 *
 * @author bff
 */
@Service
public class ScenarioHandlerRegistry {

  private final Map<String, BusinessScenarioHandler> handlerMap;

  public ScenarioHandlerRegistry(List<BusinessScenarioHandler> handlers) {
    this.handlerMap = handlers.stream()
      .flatMap(h -> h.supportedTypes().stream().map(t -> Map.entry(t, h)))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * 根据业务类型查找处理器。
   *
   * @param businessType 业务类型标识（与前端请求中的 businessType 字段一致）
   * @return 对应的场景处理器
   * @throws BusinessException 当业务类型无对应 handler 时
   */
  public BusinessScenarioHandler handlerFor(String businessType) {
    BusinessScenarioHandler handler = handlerMap.get(businessType);
    if (handler == null) {
      throw new BusinessException(BffErrorCode.SCENARIO_NOT_SUPPORTED)
        .withLogDetail("businessType=" + businessType + ", registered=" + handlerMap.keySet());
    }
    return handler;
  }
}
