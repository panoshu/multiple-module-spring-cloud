package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.repository.Repository;

import java.util.Optional;

/**
 * 二次授权会话聚合根仓储接口。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface SecondaryAuthSessionRepository extends Repository<SecondaryAuthSession, SecondaryAuthSessionId> {

  /**
   * 查找柜员当前生效的会话(AUTHORIZED 状态且未过期)。
   *
   * <p>用于柜员办理业务前校验是否处于二次授权有效期内。
   *
   * @param tellerId 柜员用户 ID
   * @return 生效会话(可能为空)
   */
  Optional<SecondaryAuthSession> findEffectiveByTeller(Long tellerId);

  /**
   * 查找柜员待处理的授权请求(PENDING 状态)。
   *
   * <p>用于柜员登录时检查是否有未完成的授权。
   *
   * @param tellerId 柜员用户 ID
   * @return 待处理会话(可能为空)
   */
  Optional<SecondaryAuthSession> findPendingByTeller(Long tellerId);
}
