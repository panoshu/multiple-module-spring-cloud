package com.example.core.domain.gateway;

import com.example.core.domain.vauleobject.BusinessMetaContext;
import com.example.core.domain.vauleobject.config.ExtractorConfig;
import com.example.core.domain.vauleobject.config.FormParsingConfig;
import com.example.core.domain.vauleobject.config.MaterialRuleConfig;
import com.example.core.domain.vauleobject.config.StepRouteConfig;
import com.example.core.domain.vauleobject.enums.workflow.ApplicationFlowStep;

import java.util.List;
import java.util.Map;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 10:37
 */
public interface BusinessConfigGateway {
  /**
   * 查询下一步路由配置
   */
  StepRouteConfig getNextStep(BusinessMetaContext context, ApplicationFlowStep currentStep);

  /**
   * 查询材料规则配置列表
   */
  List<MaterialRuleConfig> getMaterialRules(BusinessMetaContext context);

  /**
   * 查询当前业务维度适用的事实提取器
   * 注意：此时传入的 context 中的 extensionFacts 尚未填充（因为还没执行提取器）
   */
  ExtractorConfig getExtractorConfig(BusinessMetaContext context);

  /**
   * 查询表单解析规则配置
   * 返回通用的 Map 结构，核心域不感知其内部结构，由具体的文件解析引擎解释执行
   */
  Map<String, Object> getFormParseRule(BusinessMetaContext context);

  /**
   * 根据当前业务上下文，获取表单解析配置
   */
  FormParsingConfig getFormParsingConfig(BusinessMetaContext context);

  /**
   * 查询落库处理规则
   */
  String getIngestion(BusinessMetaContext context);
}
