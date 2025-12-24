package com.example.gateway.checker;

import com.example.gateway.order.GatewayFilterOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.WebFilter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FilterOrderChecker implements ApplicationRunner {

  private static final Set<Integer> VALID_ORDERS =
    Arrays.stream(GatewayFilterOrder.values())
      .map(GatewayFilterOrder::value)
      .collect(Collectors.toSet());

  // 自定义包前缀，避免检查 Spring 内置 filter
  private static final String CUSTOM_PACKAGE_PREFIX = "com.example.";

  private final List<GlobalFilter> globalFilters;
  private final List<WebFilter> webFilters;

  @Override
  public void run(@NonNull ApplicationArguments args) {
    List<Object> customFilters = getCustomFilters();
    Map<Integer, List<String>> orderMap = buildOrderMap(customFilters);
    printOrderMap(orderMap);
  }

  // ------------------- 步骤 1：获取自定义包过滤器 -------------------
  private List<Object> getCustomFilters() {
    return Stream.concat(globalFilters.stream(), webFilters.stream())
      .filter(f -> f.getClass().getName().startsWith(CUSTOM_PACKAGE_PREFIX))
      .collect(Collectors.toList());
  }

  // ------------------- 步骤 2 & 3：解析 order 并校验 -------------------
  private int resolveAndValidateOrder(Object filter) {
    if (!(filter instanceof Ordered ordered)) {
      throw new IllegalStateException(
        String.format("GlobalFilter [%s] 未指定 order, 请实现 Ordered 接口或添加 @Order 注解",
          filter.getClass().getName())
      );
    }

    int order = ordered.getOrder();

    if (!VALID_ORDERS.contains(order)) {
      throw new IllegalStateException(
        String.format("GlobalFilter [%s] (order=%s) 未使用 GatewayFilterOrder 枚举",
          filter.getClass().getName(), order)
      );
    }

    return order;
  }

  // ------------------- 步骤 4：构建 order map 并检查冲突 -------------------
  private Map<Integer, List<String>> buildOrderMap(List<Object> customFilters) {
    Map<Integer, List<String>> orderMap = new TreeMap<>();

    for (Object filter : customFilters) {
      int order = resolveAndValidateOrder(filter);
      orderMap.computeIfAbsent(order, k -> new ArrayList<>())
        .add(filter.getClass().getName());
    }

    checkOrderConflicts(orderMap);
    return orderMap;
  }

  private void checkOrderConflicts(Map<Integer, List<String>> orderMap) {
    orderMap.forEach((order, list) -> {
      if (list.size() > 1) {
        throw new IllegalStateException(
          String.format("Filter Order 冲突: order=%d%n%s", order, String.join("\n", list))
        );
      }
    });
  }

  // ------------------- 步骤 5：打印顺序信息 -------------------
  private void printOrderMap(Map<Integer, List<String>> orderMap) {
    log.info("=== Gateway Filter Execution Order ===");
    orderMap.forEach((order, classNames) ->
      classNames.forEach(className ->
        log.info("{} → {}", String.format("%6d", order), className)
      )
    );
    log.info("======================================");
  }
}
