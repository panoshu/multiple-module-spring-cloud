package com.example.shared.id.metadata;

import com.example.shared.exception.SystemException;
import com.example.shared.id.errorcode.IdErrorCode;
import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * 基于注解的 ID 元数据解析器
 * <p>
 * 重构说明：将复杂的 resolve 逻辑拆分为 泛型解析、配置校验、构造器查找、属性计算 等独立步骤。
 */
public class AnnotationIdMetadataResolver implements IdMetadataResolver {

  private static final Pattern LONG_FORMAT_PATTERN = Pattern.compile("^[0-9%sdp]+$");

  @Override
  public IdMeta resolve(Class<?> idClass) {
    // 1. 获取并检查注解
    IdDefinition annotation = getAndCheckAnnotation(idClass);

    // 2. 解析泛型类型 (确定是 String 还是 Long)
    Class<?> paramType = resolveIdDataType(idClass);
    IdDataType dataType = determineDataTypeEnum(paramType);

    // 3. 校验配置合法性 (特别是针对 Long 类型的特殊校验)
    validateConfiguration(idClass, annotation, dataType);

    // 4. 查找构造函数句柄
    MethodHandle constructorHandle = findConstructorHandle(idClass, paramType);

    // 5. 计算基础名称 (BaseName)
    String baseName = resolveBaseName(idClass, annotation);

    // 6. 组装并返回元数据
    return buildIdMeta(annotation, dataType, paramType, baseName, constructorHandle);
  }

  // =========================================================
  // 1. 注解获取
  // =========================================================
  private IdDefinition getAndCheckAnnotation(Class<?> idClass) {
    IdDefinition annotation = idClass.getAnnotation(IdDefinition.class);
    if (annotation == null) {
      throw new SystemException(IdErrorCode.ID_CONFIG_ERROR)
        .withLogDetail("Missing @IdDefinition on " + idClass.getName());
    }
    return annotation;
  }

  // =========================================================
  // 2. 泛型解析
  // =========================================================
  private Class<?> resolveIdDataType(Class<?> idClass) {
    // 优先查找父类 (针对 class User extends Identifier<Long>)
    Type targetType = idClass.getGenericSuperclass();

    // 如果父类不是泛型 (针对 record User(...) implements Identifier<Long>)，则查找接口
    if (!(targetType instanceof ParameterizedType)) {
      for (Type iface : idClass.getGenericInterfaces()) {
        if (iface instanceof ParameterizedType pt && Identifier.class.isAssignableFrom((Class<?>) pt.getRawType())) {
          targetType = iface;
          break;
        }
      }
    }

    if (targetType instanceof ParameterizedType pt) {
      Type[] args = pt.getActualTypeArguments();
      if (args.length > 0 && args[0] instanceof Class<?> clz) {
        if (Long.class.isAssignableFrom(clz)) {
          return Long.class;
        }
        if (Integer.class.isAssignableFrom(clz)) {
          return Integer.class;
        }
      }
    }
    return String.class; // 默认为 String
  }

  private IdDataType determineDataTypeEnum(Class<?> paramType) {
    if (paramType == Long.class) {
      return IdDataType.LONG;
    }
    if (paramType == Integer.class) {
      return IdDataType.INTEGER;
    }
    return IdDataType.STRING;
  }

  // =========================================================
  // 3. 配置校验
  // =========================================================
  private void validateConfiguration(Class<?> idClass, IdDefinition annotation, IdDataType dataType) {
    if (dataType == IdDataType.LONG) {
      // 校验1: 类型限制
      if (annotation.type() != IdType.SEGMENT && annotation.type() != IdType.SNOWFLAKE) {
        throw new SystemException(IdErrorCode.ID_CONFIG_ERROR)
          .withLogDetail("Long ID type is only supported for SEGMENT or SNOWFLAKE. Class: " + idClass.getName());
      }
      // 校验2: 格式化模板安全性
      String format = annotation.format();
      if (!LONG_FORMAT_PATTERN.matcher(format).matches() || format.contains("%n")) {
        throw new SystemException(IdErrorCode.ID_CONFIG_ERROR)
          .withLogDetail("Invalid format for Long ID: '" + format + "'. Long IDs only support numeric characters, %s, %d, and %p. Class: " + idClass.getName());
      }
    }
  }

  // =========================================================
  // 4. 构造器查找
  // =========================================================
  private MethodHandle findConstructorHandle(Class<?> idClass, Class<?> paramType) {
    try {
      MethodType methodType = MethodType.methodType(void.class, paramType);
      return MethodHandles.publicLookup().findConstructor(idClass, methodType);
    } catch (NoSuchMethodException | IllegalAccessException e) {
      throw new SystemException(IdErrorCode.ID_STRUCTURE_ERROR, e)
        .withLogDetail("%s must have a public constructor(%s)".formatted(idClass.getName(), paramType.getName()));
    }
  }

  // =========================================================
  // 5. 基础名称计算
  // =========================================================
  private String resolveBaseName(Class<?> idClass, IdDefinition annotation) {
    String configuredName = annotation.name();
    if (!configuredName.isEmpty()) {
      return configuredName;
    }
    // 默认策略：类名去 "Id" 后缀转大写
    String simpleName = idClass.getSimpleName();
    return simpleName.endsWith("Id")
      ? simpleName.substring(0, simpleName.length() - 2).toUpperCase()
      : simpleName.toUpperCase();
  }

  // =========================================================
  // 6. 对象组装
  // =========================================================
  private IdMeta buildIdMeta(IdDefinition annotation, IdDataType dataType, Class<?> paramType,
                             String baseName, MethodHandle constructorHandle) {
    DateTimeFormatter dtf = DateTimeFormatter.ofPattern(annotation.dateFormat());
    String format = annotation.format();
    String seqKey = annotation.seqKey();

    return new IdMeta(
      annotation.type(),
      dataType,
      baseName,
      format,
      seqKey,
      dtf,
      constructorHandle,
      annotation.seqLength(),
      "%s".equals(format), // isSimpleSequence
      format.contains("%d"), // hasDateInFormat
      format.contains("%p"), // hasPrefixInFormat
      seqKey.contains("%d"), // hasDateInKey
      seqKey.contains("%p")  // hasPrefixInKey
    );
  }
}
