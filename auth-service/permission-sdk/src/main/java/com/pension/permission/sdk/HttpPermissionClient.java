package com.pension.permission.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * PermissionClient的HTTP实现，只用java.net.http.HttpClient(JDK11+自带)，不引入任何HTTP客户端库。
 * <p>
 * 请求/响应体没有用JSON库，是手工拼接/解析的极简格式——牺牲一点点代码简洁度，
 * 换来这个SDK对业务服务来说是真正的零依赖(不会因为SDK内置了某个版本的Jackson/Gson，
 * 跟宿主服务自己用的版本冲突)。如果你们的技术栈能接受，也可以自己包一层用喜欢的HTTP客户端
 * /JSON库重新实现PermissionClient接口，SDK本身不强制。
 */
public final class HttpPermissionClient implements PermissionClient {

  private final HttpClient httpClient;
  private final URI baseUri;
  private final Supplier<String> serviceTokenSupplier;
  private final Duration timeout;

  public HttpPermissionClient(URI baseUri, Supplier<String> serviceTokenSupplier) {
    this(baseUri, serviceTokenSupplier, Duration.ofSeconds(2));
  }

  public HttpPermissionClient(URI baseUri, Supplier<String> serviceTokenSupplier, Duration timeout) {
    this.httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    this.baseUri = baseUri;
    this.serviceTokenSupplier = serviceTokenSupplier;
    this.timeout = timeout;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  @Override
  public boolean checkPermission(String accountId, String planId, String businessCode, String actionCode) {
    String query = "accountId=" + encode(accountId)
      + "&planId=" + encode(planId)
      + "&businessCode=" + encode(businessCode)
      + "&actionCode=" + encode(actionCode == null ? "" : actionCode);
    String body = get("/internal/permissions/check?" + query);
    return parseAllowed(body);
  }

  @Override
  public Map<PermissionCheckRequest, Boolean> checkPermissions(
    String accountId, String planId, List<PermissionCheckRequest> items) {
    // 批量场景：把多个(business,action)拼进一次请求体，减少网络往返。
    // 请求体格式： businessCode1:actionCode1,businessCode2:actionCode2,...
    String itemsParam = items.stream()
      .map(i -> encode(i.businessCode()) + ":" + encode(i.actionCode() == null ? "" : i.actionCode()))
      .collect(Collectors.joining(","));
    String query = "accountId=" + encode(accountId) + "&planId=" + encode(planId) + "&items=" + itemsParam;
    String body = get("/internal/permissions/check-batch?" + query);

    // 响应约定：businessCode1:actionCode1=true,businessCode2:actionCode2=false
    Map<PermissionCheckRequest, Boolean> result = new LinkedHashMap<>();
    for (PermissionCheckRequest item : items) {
      String key = item.businessCode() + ":" + (item.actionCode() == null ? "" : item.actionCode());
      result.put(item, body.contains(key + "=true"));
    }
    return result;
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
          "Permission服务返回异常状态码: " + response.statusCode());
      }
      return response.body();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new PermissionServiceUnavailableException("调用Permission服务失败", e);
    }
  }

  private boolean parseAllowed(String body) {
    // 响应体约定的极简格式：{"allowed":true} 或 {"allowed":false}
    return body.contains("\"allowed\":true");
  }
}
