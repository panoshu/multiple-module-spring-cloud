package com.pension.permission.sdk;

import com.pension.permission.sdk.dto.PermissionDTO;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

/**
 * PermissionCacheClient 的 HTTP 实现。
 */
public final class HttpPermissionCacheClient implements PermissionCacheClient {

  private final HttpClient httpClient;
  private final URI baseUri;
  private final Supplier<String> serviceTokenSupplier;
  private final Duration timeout;

  public HttpPermissionCacheClient(URI baseUri, Supplier<String> serviceTokenSupplier) {
    this(baseUri, serviceTokenSupplier, Duration.ofSeconds(2));
  }

  public HttpPermissionCacheClient(URI baseUri, Supplier<String> serviceTokenSupplier, Duration timeout) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.baseUri = baseUri;
    this.serviceTokenSupplier = serviceTokenSupplier;
    this.timeout = timeout;
  }

  @Override
  public Set<PermissionDTO> getPlatformPermissions(String accountId) {
    String path = "/internal/permission-cache/platform?accountId=" + encode(accountId);
    String body = get(path);
    return Collections.emptySet();  // 由实际 JSON 解析逻辑填充
  }

  @Override
  public Set<PermissionDTO> getBusinessPermissions(String accountId, String planId) {
    String path = "/internal/permission-cache/business?accountId=" + encode(accountId)
      + "&planId=" + encode(planId);
    String body = get(path);
    return Collections.emptySet();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private String get(String pathAndQuery) {
    HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(pathAndQuery))
      .header("Authorization", "Bearer " + serviceTokenSupplier.get())
      .timeout(timeout)
      .GET()
      .build();
    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new PermissionServiceUnavailableException(
          "Permission Cache 服务返回异常状态码: " + response.statusCode());
      }
      return response.body();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new PermissionServiceUnavailableException("调用 Permission Cache 服务失败", e);
    }
  }
}
