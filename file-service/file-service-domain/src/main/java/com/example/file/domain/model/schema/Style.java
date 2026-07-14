package com.example.file.domain.model.schema;

// 使用 Record 定义不可变的值对象
public record Style(boolean fontBold, String fontColor, String backgroundColor) {
  public static final Style DEFAULT = new Style(false, "BLACK", "WHITE");
}
