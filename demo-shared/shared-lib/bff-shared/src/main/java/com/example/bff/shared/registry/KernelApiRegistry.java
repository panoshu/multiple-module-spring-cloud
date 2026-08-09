package com.example.bff.shared.registry;

import com.example.core.api.application.BusinessApplicationApi;
import com.example.core.api.batch.BusinessBatchApi;
import com.example.core.api.form.BusinessFormApi;
import com.example.core.api.material.MaterialAppApi;
import com.example.core.api.progress.BusinessProgressApi;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kernel API 代理注册表
 *
 * <p>为每个服务按需创建 kernel API 代理。使用 {@link HttpServiceProxyFactory} 从
 * {@code @HttpExchange} 注解自动读取路径，{@code @LoadBalanced} 使服务名通过
 * LoadBalancer 解析到实际实例。代理创建后缓存，后续调用直接复用。
 *
 * <p>线程安全说明：{@link ConcurrentHashMap#computeIfAbsent} 保证每个 serviceName
 * 只创建一次代理。每次创建时调用 {@link RestClient.Builder#clone()} 获得独立副本，
 * 避免共享 Builder 的 baseUrl 互相覆盖。
 *
 * @author bff
 */
public class KernelApiRegistry {

  private final RestClient.Builder restClientBuilder;
  private final Map<String, ApiProxies> proxiesByService = new ConcurrentHashMap<>();

  public KernelApiRegistry(RestClient.Builder restClientBuilder) {
    this.restClientBuilder = restClientBuilder;
  }

  /**
   * 获取指定服务的批次管理 API 代理。
   */
  public BusinessBatchApi getBatchApi(String serviceName) {
    return getProxies(serviceName).batchApi();
  }

  /**
   * 获取指定服务的表单管理 API 代理。
   */
  public BusinessFormApi getFormApi(String serviceName) {
    return getProxies(serviceName).formApi();
  }

  /**
   * 获取指定服务的申请单管理 API 代理。
   */
  public BusinessApplicationApi getApplicationApi(String serviceName) {
    return getProxies(serviceName).applicationApi();
  }

  /**
   * 获取指定服务的材料管理 API 代理。
   */
  public MaterialAppApi getMaterialApi(String serviceName) {
    return getProxies(serviceName).materialApi();
  }

  /**
   * 获取指定服务的进度查询 API 代理。
   */
  public BusinessProgressApi getProgressApi(String serviceName) {
    return getProxies(serviceName).progressApi();
  }

  private ApiProxies getProxies(String serviceName) {
    return proxiesByService.computeIfAbsent(serviceName, this::createProxies);
  }

  private ApiProxies createProxies(String serviceName) {
    RestClient client = restClientBuilder
      .clone()
      .baseUrl("http://" + serviceName)
      .build();
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(client))
      .build();

    return new ApiProxies(
      factory.createClient(BusinessBatchApi.class),
      factory.createClient(BusinessFormApi.class),
      factory.createClient(BusinessApplicationApi.class),
      factory.createClient(MaterialAppApi.class),
      factory.createClient(BusinessProgressApi.class)
    );
  }

  /**
   * 判断指定服务是否已创建代理。
   */
  public boolean contains(String serviceName) {
    return proxiesByService.containsKey(serviceName);
  }
}
