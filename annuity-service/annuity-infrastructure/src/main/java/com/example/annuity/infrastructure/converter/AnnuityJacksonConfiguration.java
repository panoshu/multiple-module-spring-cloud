package com.example.annuity.infrastructure.converter;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.infrastructure.configuration.LibJacksonModule;
import com.example.core.infrastructure.json.BusinessExtensionMixIn;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 年金服务 Jackson 配置
 * <p>
 * 继承 kernel 的 {@link BusinessExtensionMixIn}，注册 {@link AnnuityApplicationExtension}
 * 作为 {@link BusinessExtension} 的多态子类型。通过 {@code businessType} 字段（取值
 * {@code ACC_PLAN_CREATE} / {@code ACC_PLAN_MODIFY} / {@code ACC_PLAN_DELETE}）作为类型标识符。
 * <p>
 * 该配置同时被 {@link ApplicationDataConverter#OBJECT_MAPPER} 静态引用，保证 DO ↔ 领域对象
 * 转换时与 Spring 上下文中的 ObjectMapper 行为一致。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@AutoConfiguration
public class AnnuityJacksonConfiguration {

  /**
   * 配置 ObjectMapper：先调用 kernel 的 {@link LibJacksonModule#registerMixIns} 注册基础 Mix-in，
   * 再通过子类 Mix-in 注册 AnnuityApplicationExtension 子类型。
   */
  public static ObjectMapper configure(ObjectMapper mapper) {
    LibJacksonModule.registerMixIns(mapper);
    mapper.addMixIn(BusinessExtension.class, AnnuityExtensionMixIn.class);
    return mapper;
  }

  /**
   * Spring 上下文中的 ObjectMapper Bean
   */
  @Bean
  public ObjectMapper annuityObjectMapper() {
    return configure(new ObjectMapper());
  }

  /**
   * 年金专属 Mix-in：在 kernel 基础 Mix-in 上注册 AnnuityApplicationExtension 子类型。
   * <p>
   * {@code businessType} 字段的枚举值（{@code ACC_PLAN_CREATE/MODIFY/DELETE}）作为
   * 多态类型标识符，与 {@link AnnuityApplicationExtension#businessType()} 的取值一一对应。
   * <p>
   * <b>【为何重复声明 @JsonTypeInfo】</b>Jackson 的 mix-in 注解解析不会从父接口继承
   * {@code @JsonTypeInfo}，必须在本接口上直接声明，否则反序列化时无法根据
   * {@code businessType} 属性定位具体子类型。
   */
  @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.EXISTING_PROPERTY,
      property = "businessType",
      visible = true
  )
  @JsonSubTypes({
      @JsonSubTypes.Type(value = AnnuityApplicationExtension.class, name = "ACC_PLAN_CREATE"),
      @JsonSubTypes.Type(value = AnnuityApplicationExtension.class, name = "ACC_PLAN_MODIFY"),
      @JsonSubTypes.Type(value = AnnuityApplicationExtension.class, name = "ACC_PLAN_DELETE")
  })
  public interface AnnuityExtensionMixIn extends BusinessExtensionMixIn {
  }
}
