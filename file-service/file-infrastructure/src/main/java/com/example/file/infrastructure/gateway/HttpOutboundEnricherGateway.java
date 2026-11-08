package com.example.file.infrastructure.gateway;

import com.example.file.api.OutboundDataEnrichmentApi;
import com.example.file.domain.gateway.OutboundEnricherGateway;
import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 出站数据增强网关实现（2026-11-08 批次5：enricher 接线）.
 *
 * <p>按模板配置注入的端点 URL 构造 {@link OutboundDataEnrichmentApi}
 * HttpService 代理（{@link java.util.concurrent.ConcurrentHashMap} 缓存代理
 * ——端点 URL 有限集）；协议违约（非成功码/行数不对应）抛
 * {@link SystemException}——fail-fast（设计已定稿：金融数据完整性优先，
 * 不降级导出）.</p>
 *
 * <p>业务侧实现 kernel 的 {@code AbstractOutboundEnrichmentEndpoint}
 * 基类暴露端点，端点 URL 由导出模板配置注入（运行时路由，编译零依赖）.</p>
 *
 * @author file-service
 */
@Slf4j
@Component
public class HttpOutboundEnricherGateway implements OutboundEnricherGateway {

  private final Map<String, OutboundDataEnrichmentApi> clientCache = new ConcurrentHashMap<>();

  @Override
  public List<Map<String, Object>> invoke(String endpoint, String subTaskId, String fileTaskId,
                                           List<Map<String, Object>> baseRows) {
    if (endpoint == null || endpoint.isBlank() || baseRows == null || baseRows.isEmpty()) {
      return List.of();
    }
    OutboundDataEnrichmentApi client = clientCache.computeIfAbsent(endpoint, this::buildClient);
    var request = new OutboundDataEnrichmentApi.EnrichmentRequest(subTaskId, fileTaskId, baseRows);
    var response = client.enrich(request);
    if (response == null || !response.isSuccess() || response.data() == null) {
      throw new SystemException(CommonError.INTERNAL_SERVER_ERROR)
        .withUserDetail("导出数据增强失败")
        .withLogDetail("enricher 调用失败: endpoint=" + endpoint + ", subTaskId=" + subTaskId
          + ", code=" + (response != null ? response.code() : "null"));
    }
    List<Map<String, Object>> enriched = response.data();
    if (enriched.size() != baseRows.size()) {
      throw new SystemException(CommonError.INTERNAL_SERVER_ERROR)
        .withUserDetail("导出数据增强协议违约（行数不对应）")
        .withLogDetail("enricher 行数 " + enriched.size() + " vs baseRows " + baseRows.size()
          + ", subTaskId=" + subTaskId);
    }
    log.info("导出数据增强完成: subTaskId={}, rows={}", subTaskId, enriched.size());
    return enriched;
  }

  private OutboundDataEnrichmentApi buildClient(String endpoint) {
    HttpServiceProxyFactory factory = HttpServiceProxyFactory
      .builderFor(RestClientAdapter.create(RestClient.builder().baseUrl(endpoint).build()))
      .build();
    return factory.createClient(OutboundDataEnrichmentApi.class);
  }
}
