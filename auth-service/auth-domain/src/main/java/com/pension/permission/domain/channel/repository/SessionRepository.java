package com.pension.permission.domain.channel.repository;


import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.types.SessionId;

import java.util.Optional;


public interface SessionRepository extends Repository<Session, SessionId> {

  /**
   * 按主账号查找当前会话.
   *
   * <p>用于二次授权事件监听场景：根据柜员账号定位其活跃的渠道会话，
   * 以便在 {@code SecondaryAuthCompleted}/{@code SecondaryAuthRevoked} 事件触发时
   * 同步更新会话的有效身份。</p>
   *
   * @param primaryAccountId 主账号
   * @return 会话（若存在）
   */
  Optional<Session> findByPrimaryAccountId(UserNo primaryAccountId);
}
