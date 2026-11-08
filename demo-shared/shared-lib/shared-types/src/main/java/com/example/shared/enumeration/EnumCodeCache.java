package com.example.shared.enumeration;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 枚举code缓存
 */
public final class EnumCodeCache {
  private static final ConcurrentHashMap<Class<?>, Map<Serializable, Enum<?>>> CACHE = new ConcurrentHashMap<>();

  private EnumCodeCache() {
  }

  /**
   * 获取枚举
   */
  @SuppressWarnings("unchecked")
  public static <E extends Enum<E> & CodeEnum<C>, C extends Serializable> E get(Class<E> enumClass, C code) {

    if (code == null) {
      return null;
    }

    Map<Serializable, Enum<?>> map =
      CACHE.computeIfAbsent(
        enumClass,
        EnumCodeCache::build
      );

    return (E) map.get(code);
  }

  /**
   * 创建缓存
   */
  private static Map<Serializable, Enum<?>> build(
    Class<?> enumClass
  ) {
    Object[] constants = enumClass.getEnumConstants();

    if (constants == null) {
      throw new IllegalArgumentException(
        enumClass + " is not enum"
      );
    }


    return Arrays.stream(constants)
      .map(e -> (Enum<?> & CodeEnum<?>) e)
      .collect(
        Collectors.toUnmodifiableMap(
          CodeEnum::getCode,
          e -> e
        )
      );
  }

  /**
   * 清理缓存
   */
  public static void clear() {
    CACHE.clear();
  }

}
