package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.types.LoginLogId;
import com.example.shared.domain.repository.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 登录日志聚合根仓储接口。
 *
 * <p>定义登录审计与风控相关的查询语义,实现位于 {@code iam-infrastructure} 层。
 * 仅支持追加写入({@link #save}),不支持删除与更新业务字段;
 * {@link #findRecentFailures} 用于风控(连续失败次数统计)。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface LoginLogRepository extends Repository<LoginLog, LoginLogId> {

  /**
   * 查询某用户在指定时间窗口内的失败登录记录(按时间倒序)。
   *
   * <p>用于风控判断:例如"近 5 分钟失败次数是否超过阈值"。
   *
   * @param userId      用户 ID
   * @param channelType 渠道类型
   * @param since       起始时间(含)
   * @return 失败日志列表(可能为空,不会返回 null)
   */
  List<LoginLog> findRecentFailures(Long userId, ChannelType channelType, LocalDateTime since);

  /**
   * 查询某登录名在指定时间窗口内的失败登录记录(用于用户不存在场景的风控)。
   *
   * @param loginName   登录名
   * @param channelType 渠道类型
   * @param since       起始时间(含)
   * @return 失败日志列表(可能为空)
   */
  List<LoginLog> findRecentFailuresByLoginName(String loginName, ChannelType channelType, LocalDateTime since);

  /**
   * 统计某用户在指定时间窗口内的失败登录次数。
   *
   * <p>等价于 {@link #findRecentFailures} 的结果大小,但仅需要计数时使用此方法以减少数据传输。
   *
   * @param userId      用户 ID
   * @param channelType 渠道类型
   * @param since       起始时间(含)
   * @return 失败次数
   */
  int countRecentFailures(Long userId, ChannelType channelType, LocalDateTime since);

  /**
   * 查询某用户的最近一次登录日志(成功或失败)。
   *
   * @param userId      用户 ID
   * @param channelType 渠道类型
   * @return 最近登录日志(可能为空)
   */
  Optional<LoginLog> findLatestByUser(Long userId, ChannelType channelType);
}
