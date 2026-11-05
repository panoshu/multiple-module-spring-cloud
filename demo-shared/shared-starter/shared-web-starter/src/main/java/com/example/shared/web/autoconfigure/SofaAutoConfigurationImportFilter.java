package com.example.shared.web.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;

/**
 * SOFA 注册中心自动装配候选过滤器（SOFA→Nacos 注册切换的最终形态）.
 *
 * <p><b>背景</b>：SOFA 注册在本项目中从未通过启动类注解开启，而是
 * {@code spring-cloud-starter-sofa-registry}（shared-web-starter 引入）的
 * classpath 自动装配行为——在自动装配<b>候选阶段</b>直接过滤掉 SOFA 全套
 * 注册/发现/路由自动装配类，pom 依赖与 application-sofa.yml 配置文件原样保留.
 *
 * <p><b>为什么不用 {@code spring.autoconfigure.exclude} 属性</b>：属性是
 * "第一个定义者独占"——任何测试/应用自己设置了 exclude 清单（如 BFF 测试
 * 的 Nacos 排除表）都会使共享层无法再追加 SOFA 类，导致 SOFA 装配"漏网"
 * （实际发生过：SofaServiceRegistry 因缺 SofaRegistryService bean 装配失败）.
 * ImportFilter 在候选裁剪阶段生效，与任何应用级 exclude 互不干扰、总是生效.
 *
 * <p><b>注册由 Nacos 接管</b>：{@code spring-cloud-starter-alibaba-nacos-discovery}
 * （同 starter 引入），各服务 application-local.yml 已预置
 * {@code spring.cloud.nacos.discovery.server-addr}.
 *
 * <p><b>切回 SOFA</b>：删除本 starter {@code META-INF/spring.factories} 中的
 * ImportFilter 注册行即可（依赖与配置从未删除）.
 *
 * <p>排除清单与 channel-gateway 测试配置验证过的自动装配类一致（注册、
 * 发现、路由、负载均衡、健康端点、工具）.
 *
 * @author shared-web-starter
 */
public class SofaAutoConfigurationImportFilter implements AutoConfigurationImportFilter {

  private static final java.util.Set<String> SOFA_AUTOCONFIG_CLASSES = java.util.Set.of(
    "com.alipay.cloud.dsr.client.SofaRegistryAutoConfiguration",
    "com.alipay.cloud.dsr.registry.SofaServiceRegistryAutoConfiguration",
    "com.alipay.cloud.dsr.endpoint.SofaDiscoveryEndpointAutoConfiguration",
    "com.alipay.cloud.dsr.discovery.SofaDiscoveryAutoConfiguration",
    "com.alipay.cloud.dsr.discovery.SofaDiscoveryClientConfiguration",
    "com.alipay.cloud.dsr.discovery.reactive.SofaReactiveDiscoveryClientConfiguration",
    "com.alipay.cloud.dsr.discovery.configclient.SofaConfigServerAutoConfiguration",
    "com.alipay.cloud.dsr.discovery.configclient.SofaDiscoveryClientConfigServiceBootstrapConfiguration",
    "com.alipay.cloud.dsr.loadbalancer.LoadBalancerSofaAutoConfiguration",
    "com.alipay.cloud.dsr.router.SofaRegistryRouterAutoConfiguration",
    "com.alipay.cloud.dsr.util.InetUtilAutoConfiguration");

  @Override
  public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
    boolean[] match = new boolean[autoConfigurationClasses.length];
    for (int i = 0; i < autoConfigurationClasses.length; i++) {
      match[i] = autoConfigurationClasses[i] == null
        || !SOFA_AUTOCONFIG_CLASSES.contains(autoConfigurationClasses[i]);
    }
    return match;
  }
}
