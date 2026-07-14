package com.example.shared.web.trace.util;


import com.example.shared.web.core.annotation.BizTag;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 带缓存的反射工具类 (Flyweight 思想)
 */
public class BizTagReflectionExtractor {

  private static final Map<Class<?>, List<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

  public static List<Field> getAnnotatedFields(Class<?> clazz) {
    if (clazz == null || isJdkType(clazz)) {
      return List.of();
    }

    return FIELD_CACHE.computeIfAbsent(clazz, k -> {
      var list = new ArrayList<Field>();
      Class<?> target = k;
      // 递归扫描父类，直到 Object 或 JDK 类型
      while (target != null && !isJdkType(target)) {
        for (Field field : target.getDeclaredFields()) {
          // 支持 @BizTag 在 Record 组件上或普通字段上
          if (field.isAnnotationPresent(BizTag.class)) {
            field.setAccessible(true);
            list.add(field);
          }
        }
        target = target.getSuperclass();
      }
      return list;
    });
  }

  // 简单判断是否为 JDK 原生类型，避免无效扫描
  private static boolean isJdkType(Class<?> clazz) {
    return clazz.getPackageName().startsWith("java.") || clazz.getPackageName().startsWith("javax.");
  }
}
