package com.example.shared.identifier.contract;

/**
 * ID 服务标准接口 (API)
 */
public interface IdService {

  /**
   * 生成强类型 ID (DDD)
   *
   * @param idClass    ID 类 (需标注 @IdDefinition)
   * @param bizContext 业务上下文 (用于替换 %p)
   */
  <T extends Identifier<K>, K> T nextId(Class<T> idClass, String bizContext);

  <T extends Identifier<K>, K> T nextId(Class<T> idClass);

  /**
   * 生成普通字符串 ID
   *
   * @param type ID 类型 (ULID, UUID_V7)
   */
  String nextId(IdType type);

  /**
   * 生成带业务语义的字符串 ID (Segment)
   *
   * @param type    ID 类型 (SEGMENT)
   * @param bizType 业务类型 (如 "ORDER")
   */
  String nextId(IdType type, String bizType);

  <T extends Identifier<Long>> T nextLongId(Class<T> idClass, String bizContext);

  Long nextLongId(String bizType);
}
