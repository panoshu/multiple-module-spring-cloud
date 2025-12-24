package com.example.share.logging.obfuscate.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;

/**
 * ObfuscateProperties
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:42
 */
@Validated
@ConfigurationProperties(prefix = "logbook.obfuscate")
public record ObfuscateProperties(
  @NotNull @Valid GlobalConfig global,
  @NotEmpty Map<String, @Valid FieldConfig> fields,
  Map<String, @Valid StrategyConfig> strategies
) {

  public ObfuscateProperties(
    GlobalConfig global,
    Map<String, FieldConfig> fields,
    Map<String, StrategyConfig> strategies
  ) {
    this.global = global;
    this.fields = fields != null ? Map.copyOf(fields) : Map.of();
    this.strategies = strategies != null ? Map.copyOf(strategies) : Map.of();
  }

  public record GlobalConfig(
    @DefaultValue("true") boolean enable,
    @DefaultValue("***") String replacement,
    @DefaultValue("true") boolean enableWildcardPaths
  ) {
  }

  public record FieldConfig(
    @NotEmpty List<String> aliases,
    @NotNull ObfuscationStrategyType strategy,
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
