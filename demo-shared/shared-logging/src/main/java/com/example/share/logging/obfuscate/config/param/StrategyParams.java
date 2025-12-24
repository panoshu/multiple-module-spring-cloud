package com.example.share.logging.obfuscate.config.param;

/**
 * StrategyParams
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 17:31
 */
public sealed interface StrategyParams permits PartialHideParams,
  KeepFirstLastParams, PatternRegexParams, FullParams, HashSHA256Params {}
