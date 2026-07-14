package com.example.core.domain.service.engine;

import com.example.core.domain.annotation.DomainService;
import com.example.core.domain.vauleobject.MaterialConditionContext;
import com.example.core.domain.vauleobject.MaterialItem;
import com.example.core.domain.vauleobject.config.MaterialRuleConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 通用材料规则引擎
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 17:04
 */
@DomainService
public class MaterialRuleEngine {
  /**
   * 解析规则列表，生成【全新】的材料蓝图清单
   */
  public List<MaterialItem> resolve(List<MaterialRuleConfig> rules, MaterialConditionContext context) {
    List<MaterialItem> result = new ArrayList<>();

    for (MaterialRuleConfig rule : rules) {
      // 1. 评估【激活条件】：该规则是否对当前业务生效？如果未配置，默认生效。
      String condition = rule.activationCondition();
      boolean isActivated = condition == null || condition.isBlank() || context.evaluate(condition);

      if (isActivated) {
        // 2. 规则命中，转化为不可变的领域值对象 (此时还没有任何附件)
        MaterialItem item = new MaterialItem(
          rule.materialCode(),
          rule.materialName(),
          rule.businessLevel(),
          rule.requirementType(),
          rule.materialCondition(), // 塞入必传条件，由 isSatisfied() 时使用
          Optional.empty()          // 初始化时无文件
        );
        result.add(item);
      }
    }
    return result;
  }
}
