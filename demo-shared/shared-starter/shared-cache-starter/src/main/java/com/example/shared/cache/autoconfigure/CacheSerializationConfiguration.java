package com.example.shared.cache.autoconfigure;

import com.example.shared.cache.properties.SharedCacheProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.apache.fury.Fury;
import org.apache.fury.ThreadSafeFury;
import org.apache.fury.config.Language;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "org.springframework.data.redis.serializer.RedisSerializer")
public class CacheSerializationConfiguration {

  @Bean("cacheValueSerializer")
  @ConditionalOnClass(GenericJackson2JsonRedisSerializer.class)
  @ConditionalOnProperty(prefix = "shared.cache", name = "serializer-type", havingValue = "JACKSON", matchIfMissing = true)
  public RedisSerializer<Object> jacksonSerializer(ObjectMapper baseMapper, SharedCacheProperties props) {
    ObjectMapper cacheMapper = baseMapper.copy();

    BasicPolymorphicTypeValidator.Builder validatorBuilder = BasicPolymorphicTypeValidator.builder()
      .allowIfBaseType(Object.class);

    if (props.allowedPackages() != null) {
      // 显式调用 allowIfSubType(String)，解决 Lambda 歧义
      for (String pattern : props.allowedPackages()) {
        validatorBuilder.allowIfSubType(pattern);
      }
    }

    cacheMapper.activateDefaultTyping(
      validatorBuilder.build(),
      ObjectMapper.DefaultTyping.NON_FINAL,
      JsonTypeInfo.As.PROPERTY
    );

    return new GenericJackson2JsonRedisSerializer(cacheMapper);
  }

  @Bean("cacheValueSerializer")
  @ConditionalOnClass(Fury.class)
  @ConditionalOnProperty(prefix = "shared.cache", name = "serializer-type", havingValue = "FURY")
  public RedisSerializer<Object> furySerializer() {
    return new FuryRedisSerializer();
  }

  // --- Fury 序列化实现 ---
  static class FuryRedisSerializer implements RedisSerializer<Object> {
    private final ThreadSafeFury fury;

    public FuryRedisSerializer() {
      this.fury = Fury.builder()
        .withLanguage(Language.JAVA)
        .requireClassRegistration(false) // 允许动态注册类
        .buildThreadSafeFury();
    }

    @Override
    public byte[] serialize(Object t) throws SerializationException {
      return t == null ? new byte[0] : fury.serialize(t);
    }

    @Override
    public Object deserialize(byte[] bytes) throws SerializationException {
      return (bytes == null || bytes.length == 0) ? null : fury.deserialize(bytes);
    }
  }
}
