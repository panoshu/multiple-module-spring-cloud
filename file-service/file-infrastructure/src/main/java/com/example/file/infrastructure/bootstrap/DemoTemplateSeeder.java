package com.example.file.infrastructure.bootstrap;

import com.example.file.domain.gateway.ConfigLoader;
import com.example.file.domain.model.parse.aggregate.TemplateConfig;
import com.example.file.domain.model.parse.valueobject.config.RoutingRule;
import com.example.file.domain.repository.TemplateConfigRepository;
import com.example.file.types.BizType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 演示模板配置播种器（本地/联调）.
 *
 * <p>启动时检查目标 bizType 是否已有 ACTIVE 模板配置；没有则从 classpath YAML
 * 加载并激活。幂等：已有配置时直接跳过，不会覆盖管理端数据.</p>
 *
 * <p>播种的配置附带一条无条件兜底路由规则——file-service 的 FORM 品类上传
 * 申请阶段要求同 bizType 至少存在一条可路由的 ACTIVE 配置，否则 fail-fast.</p>
 *
 * <p>生产环境禁止启用；开关默认关闭，由 {@code file.demo.template-seed.enabled=true}
 * 显式打开（本地 application-local.yml / 联调启动参数）.</p>
 *
 * @author file-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
  prefix = "file.demo.template-seed", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(TemplateSeedProperties.class)
public class DemoTemplateSeeder implements ApplicationRunner {

  private final ConfigLoader configLoader;
  private final TemplateConfigRepository configRepository;
  private final ResourceLoader resourceLoader;
  private final TemplateSeedProperties properties;

  @Override
  public void run(ApplicationArguments args) {
    BizType bizType = BizType.of(properties.getBizType());
    if (configRepository.findActive(bizType).isPresent()) {
      log.info("[TemplateSeed] bizType={} 已存在 ACTIVE 模板配置，跳过播种", bizType.value());
      return;
    }

    String baselineYaml = read(properties.getBaseline());
    List<String> sourceYamls = Arrays.stream(properties.getSourceTemplates().split(","))
      .map(String::trim)
      .filter(StringUtils::hasText)
      .map(this::read)
      .toList();
    if (sourceYamls.isEmpty()) {
      throw new IllegalStateException("[TemplateSeed] 未配置 sourceTemplates");
    }

    TemplateConfig config = configLoader.loadFromYaml(
      bizType, baselineYaml, sourceYamls, properties.getVersion(), properties.getOperator());
    // 无条件兜底路由规则：FORM 上传申请阶段可路由到该配置。
    config.attachRoutingRules(List.of(new RoutingRule(List.of(), 0)));
    config.activate();
    configRepository.save(config);
    log.info("[TemplateSeed] 已播种并激活模板配置: id={}, bizType={}, version={}",
      config.id().value(), bizType.value(), properties.getVersion());
  }

  private String read(String location) {
    Resource resource = resourceLoader.getResource(location);
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("[TemplateSeed] 读取模板配置失败: " + location, e);
    }
  }
}
