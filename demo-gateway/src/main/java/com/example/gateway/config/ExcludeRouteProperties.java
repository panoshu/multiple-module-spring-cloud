package com.example.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "gateway.exclude-routes")
public record ExcludeRouteProperties(
  Integer defaultStatus,
  List<ExcludeRule> rules
) {
  public ExcludeRouteProperties {
    rules = rules != null ? rules : List.of();
  }

  public record ExcludeRule(
    String path,
    List<String> methods,
    Integer status
  ) {
    public ExcludeRule {
      validateHttpMethods(methods);
      validateHttpStatus(status);
      methods = methods != null
        ? methods.stream()
          .filter(Objects::nonNull)
          .map(String::trim)
          .map(String::toUpperCase)
          .toList()
        : List.of();
    }

    public static void validateHttpStatus(Integer status) {
      if (status == null) {
        // null → 使用 defaultStatus，是合法情况
        return;
      }

      // 使用 Spring 自带的枚举校验，不需要手写范围
      if (HttpStatus.resolve(status) == null) {
        throw new IllegalArgumentException(
          "Invalid HTTP status code: " + status +
            ". Valid codes are defined in org.springframework.http.HttpStatus."
        );
      }
    }

    private static final Set<String> VALID_HTTP_METHODS = Arrays.stream(HttpMethod.values())
      .map(HttpMethod::name)
      .collect(Collectors.toUnmodifiableSet());

    public static void validateHttpMethods(List<String> methods) {

      if (CollectionUtils.isEmpty(methods)) {
        return;
      }

      var cleaned = methods.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(String::toUpperCase)
        .toList();

      var invalid = cleaned.stream()
        .filter(m -> !VALID_HTTP_METHODS.contains(m))
        .toList();

      if (!invalid.isEmpty()) {
        throw new IllegalArgumentException(
          "Invalid HTTP methods: " + invalid +
            ". Valid methods: " + VALID_HTTP_METHODS);
      }
    }
  }

}
