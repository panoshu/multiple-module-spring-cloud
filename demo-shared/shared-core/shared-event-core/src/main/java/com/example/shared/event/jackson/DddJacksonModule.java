package com.example.shared.event.jackson;

import com.example.shared.identifier.contract.Identifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jackson 模块：注册 Identifier 强类型 ID 的序列化器与反序列化器。
 *
 * <p>序列化：{@code Identifier<?> -> "字符串值"}（如 {@code CustomerNo("C001")} 序列化为 {@code "C001"}）。
 * <p>反序列化：{@code "字符串值" -> Identifier<?> 具体子类实例}（通过反射调用 record canonical constructor {@code new XxxId(String)}）。
 *
 * <p>该模块服务于所有使用强类型 ID 的 Request DTO（如 file-api 的 ApplyUploadTokenRequest），
 * 确保前端发送的 {@code "customerNo": "C001"} 能被正确反序列化为 {@code CustomerNo} 实例。
 *
 * <p><b>实现要点</b>：Jackson 默认不会把 {@code Identifier} 接口的 deserializer 应用到具体子类（如 CustomerNo），
 * 因此通过 {@link Deserializers} 在 setupModule 时按类型动态返回 deserializer，
 * 而非使用 {@link SimpleModule#addDeserializer} 的简单注册方式。
 */
public class DddJacksonModule extends SimpleModule {

  public DddJacksonModule() {
    // 安全地将 raw type 转换为带泛型的 Class
    @SuppressWarnings("unchecked")
    Class<Identifier<?>> identifierClass = (Class<Identifier<?>>) (Class<?>) Identifier.class;
    addSerializer(identifierClass, new IdentifierSerializer());
  }

  @Override
  public void setupModule(SetupContext context) {
    super.setupModule(context);
    // 注册 Deserializers，对 Identifier 具体子类返回 IdentifierDeserializer
    // 注意：addDeserializer 注册父接口的 deserializer 不会自动应用到具体子类，
    // 必须通过 Deserializers.findBeanDeserializer 按类型动态返回。
    context.addDeserializers(new Deserializers.Base() {
      @Override
      public JsonDeserializer<?> findBeanDeserializer(JavaType type,
                                                      DeserializationConfig config,
                                                      BeanDescription beanDesc) {
        Class<?> raw = type.getRawClass();
        // Identifier.class.isAssignableFrom(raw) 包含 Identifier 本身和所有子类
        // 但 Identifier 是接口，不能实例化，需排除
        if (Identifier.class.isAssignableFrom(raw) && !raw.isInterface() && !raw.isEnum()) {
          IdentifierDeserializer deserializer = new IdentifierDeserializer();
          deserializer.targetClass = raw;
          return deserializer;
        }
        return null;
      }
    });
  }

  public static class IdentifierSerializer extends JsonSerializer<Identifier<?>> {
    @Override
    public void serialize(Identifier<?> value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
      if (value == null) {
        gen.writeNull();
      } else {
        gen.writeString(value.value().toString());
      }
    }
  }

  /**
   * 强类型 ID 反序列化器。
   *
   * <p>通过 {@link ContextualDeserializer} 获取具体目标类型（如 CustomerNo/ProductNo/UserNo），
   * 使用反射调用 record canonical constructor {@code new XxxId(String value)} 构造实例。
   *
   * <p>构造器查找结果按 Class 缓存，避免每次反序列化都做反射查找。
   *
   * <p>当 JSON 值为 null 时，Jackson 走 {@link #getNullValue} 默认返回 null，
   * 不会调用 {@link #deserialize}，因此 record 字段为 null 时无需特殊处理。
   */
  public static class IdentifierDeserializer extends JsonDeserializer<Identifier<?>>
    implements ContextualDeserializer {

    private static final ConcurrentHashMap<Class<?>, Constructor<?>> CTOR_CACHE = new ConcurrentHashMap<>();

    private Class<?> targetClass;

    private static Constructor<?> findConstructor(Class<?> clazz) {
      try {
        // record canonical constructor: public XxxId(String value)
        return clazz.getDeclaredConstructor(String.class);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
          "Identifier class " + clazz.getName() + " must have a String-argument constructor", e);
      }
    }

    @Override
    public Identifier<?> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
      String value = p.getValueAsString();
      if (value == null) {
        return null;
      }
      try {
        Constructor<?> ctor = CTOR_CACHE.computeIfAbsent(targetClass, IdentifierDeserializer::findConstructor);
        @SuppressWarnings("unchecked")
        Identifier<?> instance = (Identifier<?>) ctor.newInstance(value);
        return instance;
      } catch (ReflectiveOperationException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof RuntimeException re) {
          throw re;
        }
        throw new IOException("Failed to deserialize " + targetClass.getName() + " from: " + value, cause);
      }
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
      IdentifierDeserializer copy = new IdentifierDeserializer();
      // 在 createContextual 中再次确认目标类型（覆盖 setupModule 时设置的默认值）
      if (ctxt.getContextualType() != null) {
        copy.targetClass = ctxt.getContextualType().getRawClass();
      } else {
        copy.targetClass = this.targetClass;
      }
      return copy;
    }
  }
}
