package com.example.shared.event.jackson;

import com.example.shared.primitives.identity.Identifier;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import java.io.IOException;

public class DddJacksonModule extends SimpleModule {

  public DddJacksonModule() {
    // 安全地将 raw type 转换为带泛型的 Class
    @SuppressWarnings("unchecked")
    Class<Identifier<?>> identifierClass = (Class<Identifier<?>>) (Class<?>) Identifier.class;
    addSerializer(identifierClass, new IdentifierSerializer());
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
}
