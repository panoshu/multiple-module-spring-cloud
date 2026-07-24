package com.example.shared.domain.repository;


import com.example.shared.domain.errorcode.SharedDomainErrorCode;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.Identifier;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 泛型 Repository 接口，用于管理聚合根的持久化
 *
 * @author <a href="mailto: me@panoshu.top">panoshu</a>
 * @version 1.0
 * @since 2025/6/1 14:20
 **/

public interface Repository<T extends AggregateRoot<ID>, ID extends Identifier<?>> {

  /**
   * 根据 ID 查找聚合根。
   *
   * @param id 聚合根的唯一标识符。
   * @return 包含聚合根的 Optional，如果不存在则返回 Optional.empty()。
   */
  Optional<T> load(ID id);

  default T loadOrThrow(ID id) {
    return load(id).orElseThrow(() ->
      new DomainException(SharedDomainErrorCode.ENTITY_NOT_FOUND)
        .withLogDetail("Entity %s not found with id: %s".formatted(
          getClass().getSimpleName().replace("Repository", ""),
          id.value()
        )
      )
    );
  }

  /**
   * 保存（新增或更新）聚合根。
   * 如果聚合根是新的，则执行插入操作；如果已存在，则执行更新操作。
   * 同时，此方法也负责发布聚合根内部注册的领域事件。
   *
   * @param aggregateRoot 要保存的聚合根。
   */
  void save(T aggregateRoot);

  /**
   * 删除指定的聚合根。
   *
   * @param aggregateRoot 要删除的聚合根。
   */
  void delete(T aggregateRoot);

  /**
   * 根据 ID 删除指定的聚合根。
   *
   * @param id 要删除聚合根的 ID。
   */
  void deleteById(ID id);

  /**
   * 查找所有聚合根。
   * 注意：在大规模应用中慎用此方法，可能导致性能问题。
   *
   * @return 所有聚合根的列表。
   */
  List<T> loadAll();

  // 还可以添加分页、排序等通用查询方法
  // Page<T> findAll(Pageable pageable);
  // List<T> findByIds(List<ID> identity);

  /**
   * 流式查询, 用于批量流式处理
   *
   * @param id        聚合根ID
   * @param processor 对每条明细的处理逻辑 (由应用层提供)
   */
  void streamByAppId(ID id, Consumer<AggregateRoot<ID>> processor);
}
