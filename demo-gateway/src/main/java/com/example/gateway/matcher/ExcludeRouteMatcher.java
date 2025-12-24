package com.example.gateway.matcher;

import com.example.gateway.config.ExcludeRouteProperties;
import com.example.gateway.config.ExcludeRouteProperties.ExcludeRule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ExcludeRouteMatcher {

  private final ExcludeRouteProperties properties;
  private final AntPathMatcher matcher = new AntPathMatcher();

  public MatchResult match(String path, String method) {
    List<ExcludeRule> rules = properties.rules();
    if (rules == null) return new MatchResult(false, null);

    for (ExcludeRule rule : rules) {

      if (!matcher.match(rule.path(), path)) continue;

      // 方法为空表示匹配所有方法
      if (rule.methods().isEmpty() || rule.methods().contains(method)) {
        return new MatchResult(true, rule.status());
      }
    }

    return new MatchResult(false, null);
  }

  public record MatchResult(boolean matched, Integer ruleStatus) {}
}
