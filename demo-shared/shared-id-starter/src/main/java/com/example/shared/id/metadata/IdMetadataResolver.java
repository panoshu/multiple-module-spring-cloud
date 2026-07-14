package com.example.shared.id.metadata;

/**
 * ID 元数据解析器接口
 */
public interface IdMetadataResolver {
  /**
   * 解析类的元数据
   *
   * @param idClass ID 类型
   * @return 元数据
   */
  IdMeta resolve(Class<?> idClass);
}
