package com.example.shared.identifier.contract;

import java.io.Serializable;

/**
 * 强类型 ID 标记接口
 *
 * @param <T> 底层存储类型 (通常是 String 或 Long)
 */
public interface Identifier<T> extends Serializable {
  T value();
}
