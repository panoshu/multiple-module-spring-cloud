package com.pension.permission.domain.authorization.repository;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.types.GrantId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Grant的仓储端口。真实实现(数据库查询/索引/缓存)属于基础设施层，
 * 这里只定义领域层需要的查询能力。
 * 注意：不再有findByDerivedFrom——角色模板派生的权限已经改成实时解析、不落库，
 * 所以Grant不会再有"由某个身份分配派生而来"这种关联需要按来源反查。
 */
public interface GrantRepository {

  /**
   * 能力层：全局配置，量级小，可整体查出常驻内存
   */
  List<Grant> findActiveCapabilityGrants(LocalDateTime at);

  /**
   * 主体层候选集合：返回"可能"覆盖该身份的Grant(非CAPABILITY类型)，
   * 具体是否真的覆盖，由调用方结合 GrantSubject#covers 再过滤一遍。
   * 真实实现可以先按UserListSubject直接命中的做索引查询，
   * 再补上该身份所在计划的PLAN_ALL_MEMBERS / PLAN_ROLE类型的Grant。
   */
  List<Grant> findCandidateSubjectGrants(UserNo identity, LocalDateTime at);

  Optional<Grant> findById(GrantId id);

  void save(Grant grant);
}
