package com.pension.permission.domain.channel.repository;

import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.types.SecondaryAuthSessionId;

import java.util.List;
import java.util.Optional;

/**
 * 二次授权会话 Repository 接口.
 */
public interface SecondaryAuthSessionRepository
  extends Repository<SecondaryAuthSession, SecondaryAuthSessionId> {

  /**
   * 查询柜员当前活跃的二次授权会话（PENDING 或 AUTHORIZED）.
   *
   * <p>用于校验柜员活跃会话唯一性不变量。</p>
   */
  Optional<SecondaryAuthSession> findActiveByTeller(UserNo tellerAccountId);

  /**
   * 查询经办人所有 AUTHORIZED 状态的会话.
   *
   * <p>用于紧急收权时撤销经办人所有授权。</p>
   */
  List<SecondaryAuthSession> findAuthorizedByApprover(UserNo approverAccountId);

  /**
   * 查询经办人所有 PENDING 状态的会话.
   *
   * <p>用于经办人查询待确认列表（未来扩展）。</p>
   */
  List<SecondaryAuthSession> findPendingByApprover(UserNo approverAccountId);

  /**
   * 查询所有超时的活跃会话.
   *
   * <p>用于定时清理任务。</p>
   */
  List<SecondaryAuthSession> findTimeoutSessions();
}
