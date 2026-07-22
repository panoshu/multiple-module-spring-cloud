package com.example.annuity.infrastructure.gateway;

import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.config.ExtractorConfig;
import com.example.core.domain.engine.aggregate.valueobject.config.FormParsingConfig;
import com.example.core.domain.engine.aggregate.valueobject.config.MaterialRuleConfig;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 基于 JSON 文件的业务配置网关实现
 * <p>
 * 演示环境用：从 classpath 下的 JSON 文件加载步骤路由、材料规则、提取器配置。
 * 生产环境应替换为配置中心（如 Nacos/Apollo）的实现。
 * <p>
 * 配置文件位置：
 * <ul>
 *   <li>{@code config/step-routes.json} - 步骤路由配置列表</li>
 *   <li>{@code config/material-rules.json} - 材料规则配置列表</li>
 *   <li>{@code config/extractor-config.json} - 事实提取器配置</li>
 * </ul>
 * 启动时一次性加载到内存缓存，运行时直接读取缓存，避免每次 IO。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Slf4j
@Component
public class JsonBusinessConfigGateway implements BusinessConfigGateway {

  private static final String STEP_ROUTES_PATH = "config/step-routes.json";
  private static final String MATERIAL_RULES_PATH = "config/material-rules.json";
  private static final String EXTRACTOR_CONFIG_PATH = "config/extractor-config.json";

  private static final String DEFAULT_PARSE_TEMPLATE_ID = "ANNUITY_DEFAULT_TEMPLATE";
  private static final String DEFAULT_INGESTION = "DEFAULT";

  private final ObjectMapper objectMapper = new ObjectMapper();

  private List<StepRouteConfig> stepRoutes;
  private List<MaterialRuleConfig> materialRules;
  private ExtractorConfig extractorConfig;

  @PostConstruct
  void loadConfigs() {
    this.stepRoutes = loadList(STEP_ROUTES_PATH, StepRouteConfig.class);
    this.materialRules = loadList(MATERIAL_RULES_PATH, MaterialRuleConfig.class);
    this.extractorConfig = loadObject(EXTRACTOR_CONFIG_PATH, ExtractorConfig.class);
    log.info("已加载业务配置: stepRoutes.size={}, materialRules.size={}, extractorName={}",
        stepRoutes.size(), materialRules.size(),
        extractorConfig != null ? extractorConfig.extractorName() : null);
  }

  @Override
  public StepRouteConfig getNextStep(BusinessMetaContext context, ApplicationFlowStep currentStep) {
    return stepRoutes.stream()
        .filter(route -> route.currentStep() == currentStep)
        .findFirst()
        .orElse(null);
  }

  @Override
  public List<MaterialRuleConfig> getMaterialRules(BusinessMetaContext context) {
    return materialRules;
  }

  @Override
  public ExtractorConfig getExtractorConfig(BusinessMetaContext context) {
    return extractorConfig;
  }

  @Override
  public Map<String, Object> getFormParseRule(BusinessMetaContext context) {
    return Collections.emptyMap();
  }

  @Override
  public FormParsingConfig getFormParsingConfig(BusinessMetaContext context) {
    return new FormParsingConfig(DEFAULT_PARSE_TEMPLATE_ID, false, Collections.emptyMap());
  }

  @Override
  public String getIngestion(BusinessMetaContext context) {
    return DEFAULT_INGESTION;
  }

  // ==========================================
  // 私有方法：JSON 资源加载
  // ==========================================

  private <T> List<T> loadList(String classpathLocation, Class<T> type) {
    Resource resource = new ClassPathResource(classpathLocation);
    if (!resource.exists()) {
      log.warn("配置文件不存在: {}, 返回空列表", classpathLocation);
      return List.of();
    }
    try (InputStream is = resource.getInputStream()) {
      return objectMapper.readValue(is,
          objectMapper.getTypeFactory().constructCollectionType(List.class, type));
    } catch (IOException e) {
      log.error("加载配置文件失败: {}", classpathLocation, e);
      return List.of();
    }
  }

  private <T> T loadObject(String classpathLocation, Class<T> type) {
    Resource resource = new ClassPathResource(classpathLocation);
    if (!resource.exists()) {
      log.warn("配置文件不存在: {}, 返回 null", classpathLocation);
      return null;
    }
    try (InputStream is = resource.getInputStream()) {
      return objectMapper.readValue(is, type);
    } catch (IOException e) {
      log.error("加载配置文件失败: {}", classpathLocation, e);
      return null;
    }
  }
}
