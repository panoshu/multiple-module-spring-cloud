package com.example.shared.id.api;

import com.example.shared.exception.SystemException;
import com.example.shared.id.errorcode.IdErrorCode;
import com.example.shared.id.handler.IdTypeHandler;
import com.example.shared.id.metadata.IdDataType;
import com.example.shared.id.metadata.IdMeta;
import com.example.shared.id.metadata.IdMetadataResolver;
import com.example.shared.id.strategy.IdGenerationStrategy;
import com.example.shared.id.strategy.IdGenerationStrategy.IdContext;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 全局 ID 生成器门面
 */
@Slf4j
public class GlobalIdGenerator implements IdService {

  private final Map<IdType, IdGenerationStrategy> strategyMap;
  private final IdMetadataResolver metadataResolver;
  private final Map<IdDataType, IdTypeHandler> typeHandlers;

  private final Map<Class<?>, IdMeta> metaCache = new ConcurrentHashMap<>();

  // 【修改】构造函数自动注入 List<IdTypeHandler>
  public GlobalIdGenerator(
    List<IdGenerationStrategy> strategies,
    IdMetadataResolver metadataResolver,
    List<IdTypeHandler> handlers) {

    this.strategyMap = strategies.stream()
      .collect(Collectors.toMap(IdGenerationStrategy::getSupportedType, Function.identity()));

    this.metadataResolver = metadataResolver;

    // 【自动注册】将 List 转为 Map
    this.typeHandlers = handlers.stream()
      .collect(Collectors.toMap(IdTypeHandler::getSupportedDataType, Function.identity()));

    log.info("GlobalIdGenerator initialized with strategies: {} and handlers: {}",
      strategyMap.keySet(), typeHandlers.keySet());
  }

  @Override
  public <T extends Identifier<?>> T nextId(Class<T> idClass) {
    return nextId(idClass, null);
  }


  @Override
  @SuppressWarnings("unchecked")
  public <T extends Identifier<?>> T nextId(Class<T> idClass, String bizContext) {
    IdMeta meta = metaCache.computeIfAbsent(idClass, metadataResolver::resolve);
    IdGenerationStrategy strategy = getStrategy(meta.type());

    // 直接从 Map 获取，无需 Switch
    IdTypeHandler handler = typeHandlers.get(meta.dataType());
    if (handler == null) {
      throw new SystemException(IdErrorCode.ID_TYPE_ERROR)
        .withLogDetail("No handler found for data type: " + meta.dataType());
    }

    LocalDateTime now = LocalDateTime.now();
    String prefix = (bizContext == null) ? "" : bizContext;

    Object rawIdValue = handler.handle(meta, strategy, prefix, now);

    try {
      return (T) meta.constructorHandle().invoke(rawIdValue);
    } catch (Throwable e) {
      throw new SystemException(IdErrorCode.ID_INSTANTIATION_ERROR, e)
        .withLogDetail("Failed to instantiate ID: " + idClass.getName());
    }
  }

  @Override
  public String nextId(IdType type) {
    return getStrategy(type).nextId(new IdContext("default", null));
  }

  @Override
  public String nextId(IdType type, String bizType) {
    return getStrategy(type).nextId(new IdContext(bizType, bizType));
  }

  @Override
  public Long nextLongId(String bizType) {
    // 直接调用 Segment 策略
    return getStrategy(IdType.SEGMENT).nextLongId(new IdContext(bizType, bizType));
  }

  @Override
  public <T extends Identifier<Long>> T nextLongId(Class<T> idClass, String bizContext) {
    // 复用通用的泛型方法，它会自动识别为 LONG 类型
    return nextId(idClass, bizContext);
  }

  private IdGenerationStrategy getStrategy(IdType type) {
    IdGenerationStrategy strategy = strategyMap.get(type);
    if (strategy == null) {
      throw new SystemException(IdErrorCode.ID_STRATEGY_MISSING)
        .withLogDetail("No spi found for type: " + type);
    }
    return strategy;
  }
}
