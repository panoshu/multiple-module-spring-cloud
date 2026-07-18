package com.example.file.types;

import com.example.shared.primitives.identity.Identifier;

/**
 * 源模板编码（语义是业务编码）
 * 例如：CUST_A_V2、CUST_B_V1
 */
public record TemplateCode(String value) implements Identifier<String> {
  public TemplateCode {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("TemplateCode empty");
    }
  }
  public static TemplateCode of(String value) { return new TemplateCode(value); }
}
