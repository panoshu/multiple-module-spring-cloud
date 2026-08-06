package com.pension.permission.sdk;

import com.pension.permission.sdk.dto.PermissionGroupDTO;
import com.pension.permission.sdk.dto.PermissionItemDTO;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * PermissionMetadataClient 的 HTTP 实现，沿用 HttpPermissionClient 的极简格式
 * （手工拼接 JSON，零依赖）。
 */
public final class HttpPermissionMetadataClient implements PermissionMetadataClient {

  private final HttpClient httpClient;
  private final URI baseUri;
  private final Supplier<String> serviceTokenSupplier;
  private final Duration timeout;

  public HttpPermissionMetadataClient(URI baseUri, Supplier<String> serviceTokenSupplier) {
    this(baseUri, serviceTokenSupplier, Duration.ofSeconds(2));
  }

  public HttpPermissionMetadataClient(URI baseUri, Supplier<String> serviceTokenSupplier, Duration timeout) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.baseUri = baseUri;
    this.serviceTokenSupplier = serviceTokenSupplier;
    this.timeout = timeout;
  }

  @Override
  public List<PermissionItemDTO> listItems(String category) {
    String path = "/internal/permission-metadata/items";
    if (category != null && !category.isEmpty()) {
      path += "?category=" + encode(category);
    }
    String body = get(path);
    // 简化解析：实际场景可引入 JSON 库
    return Collections.emptyList();  // 由实际 JSON 解析逻辑填充
  }

  @Override
  public List<PermissionGroupDTO> listGroupedItems(String category) {
    String path = "/internal/permission-metadata/items/grouped";
    if (category != null && !category.isEmpty()) {
      path += "?category=" + encode(category);
    }
    String body = get(path);
    return Collections.emptyList();
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
          "Permission Metadata 服务返回异常状态码: " + response.statusCode());
      }
      return response.body();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new PermissionServiceUnavailableException("调用 Permission Metadata 服务失败", e);
    }
  }
}
