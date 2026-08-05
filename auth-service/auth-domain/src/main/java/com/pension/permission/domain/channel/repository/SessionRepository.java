package com.pension.permission.domain.channel.repository;


import com.example.shared.annuity.AnnuityChannel;
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
   * <p><b>注意</b>：本方法未按渠道过滤，柜员在多渠道同时有活跃会话时可能返回错误渠道的会话。
   * 二次授权事件监听应优先使用
   * {@link #findActiveByPrimaryAccountIdAndChannel(UserNo, AnnuityChannel)} 显式指定
   * {@link AnnuityChannel#BANK_BRANCH}。</p>
   *
   * @param primaryAccountId 主账号
   * @return 会话（若存在）
   */
  Optional<Session> findByPrimaryAccountId(UserNo primaryAccountId);

  /**
   * 按主账号和渠道查找当前活跃会话.
   *
   * <p>用于二次授权事件监听场景：根据柜员账号 + 渠道精确定位其活跃的渠道会话，
   * 避免柜员在互联网/总部/网点多渠道同时有会话时跨渠道误绑定。</p>
   *
   * @param primaryAccountId 主账号
   * @param channel 渠道（二次授权场景固定为 {@link AnnuityChannel#BANK_BRANCH}）
   * @return 会话（若存在）
   */
  Optional<Session> findActiveByPrimaryAccountIdAndChannel(UserNo primaryAccountId, AnnuityChannel channel);
}
