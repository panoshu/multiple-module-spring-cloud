package com.example.iam.domain.authorization.repository;

import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.types.BusinessDefinitionId;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 业务定义聚合根仓储接口。
 *
 * <p>定义业务定义的查询语义,实现位于 {@code iam-infrastructure} 层。
 * 业务定义是权限规则与代办关系的元数据基础,通常预置初始化数据。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface BusinessDefinitionRepository extends Repository<BusinessDefinition, BusinessDefinitionId> {

  /**
   * 按业务编码查找业务定义(用于唯一性校验与权限规则创建时预校验)。
   *
   * @param businessCode 业务编码
   * @return 业务定义(可能为空)
   */
  Optional<BusinessDefinition> findByBusinessCode(BusinessCode businessCode);

  /**
   * 检查业务编码是否已存在(用于创建时唯一性校验)。
   *
   * @param businessCode 业务编码
   * @return 存在返回 true
   */
  boolean existsByBusinessCode(BusinessCode businessCode);

  /**
   * 查询所有业务定义(管理后台使用)。
   *
   * @return 业务定义列表
   */
  List<BusinessDefinition> findAll();

  /**
   * 按启用状态查询业务定义。
   *
   * @param active 是否启用
   * @return 业务定义列表
   */
  List<BusinessDefinition> findByActive(boolean active);
}
