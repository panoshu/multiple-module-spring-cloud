package com.example.shared.logging.sanitization.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * SanitizationProperties
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:42
 */
@Validated
@ConfigurationProperties(prefix = "shared.logging.obfuscate")
public record SanitizationProperties(
  @NotNull @Valid GlobalConfig global,
  @NotEmpty Map<String, @Valid FieldConfig> fields,
  Map<String, @Valid StrategyConfig> strategies
) {

  public SanitizationProperties(
    GlobalConfig global,
    Map<String, FieldConfig> fields,
    Map<String, StrategyConfig> strategies
  ) {
    // S-12：starter 不再携带 application.yml，脱敏默认值内聚到属性类型，
    // 宿主未显式配置时仍默认开启（安全默认），显式配置可覆盖。
    this.global = global != null ? global : new GlobalConfig(true, "***", true);
    this.fields = fields != null && !fields.isEmpty() ? Map.copyOf(fields) : defaultFields();
    this.strategies = strategies != null && !strategies.isEmpty()
      ? Map.copyOf(strategies) : defaultStrategies();
  }

  private static Map<String, FieldConfig> defaultFields() {
    return Map.of(
      "password", new FieldConfig(
        List.of("$.user.credentials.password", "$.data.personalInfo.authentication.passwd",
          "$.users[*].credentials.password", "$..password",
          "header.authorization", "header.auth-token", "query.pwd"),
        SanitizationStrategyType.PARTIAL_HIDE, "******", Map.of("show-prefix", 3, "show-suffix", 4)),
      "phone", new FieldConfig(
        List.of("$..mobile", "header.x-phone", "query.mobile"),
        SanitizationStrategyType.KEEP_FIRST_LAST, null, Map.of("show-prefix", 3, "show-suffix", 4)),
      "email", new FieldConfig(
        List.of("$.user.email", "$..email", "header.x-email", "query.email"),
        SanitizationStrategyType.PARTIAL_HIDE, null, Map.of("show-prefix", 3, "show-suffix", 3)),
      "creditCard", new FieldConfig(
        List.of("$.cardNumber", "$..creditCard", "$..cardNumber"),
        SanitizationStrategyType.PATTERN_REGEX, null,
        Map.of("pattern", "\\b\\d{4}[ -]?\\d{4}[ -]?\\d{4}[ -]?\\d{4}\\b",
          "replacement", "****-****-****-****")),
      "token", new FieldConfig(
        List.of("$..accessToken", "$..refreshToken", "header.x-api-key", "header.x-auth-token"),
        SanitizationStrategyType.HASH_SHA256, null, Map.of())
    );
  }

  private static Map<String, StrategyConfig> defaultStrategies() {
    return Map.of(
      "PARTIAL_HIDE", new StrategyConfig(Map.of("show-prefix", 3, "show-suffix", 3)),
      "KEEP_FIRST_LAST", new StrategyConfig(Map.of("show-prefix", 1, "show-suffix", 1)),
      "PATTERN_REGEX", new StrategyConfig(Map.of("replacement", "***")),
      "FULL", new StrategyConfig(Map.of()),
      "HASH_SHA256", new StrategyConfig(Map.of())
    );
  }

  public record GlobalConfig(
    @DefaultValue("true") boolean enable,
    @DefaultValue("***") String replacement,
    @DefaultValue("true") boolean enableWildcardPaths
  ) {
  }

  public record FieldConfig(
    List<String> aliases,
    @NotNull SanitizationStrategyType strategy,
    String replacement,
    Map<String, Object> params
  ) {
    public FieldConfig {
      aliases = aliases != null ? List.copyOf(aliases) : List.of();
      params = params != null ? Map.copyOf(params) : Map.of();
    }
  }

  public record StrategyConfig(
    Map<String, Object> params
  ) {
    public StrategyConfig {
      params = params != null ? Map.copyOf(params) : Map.of();
    }
  }
}
