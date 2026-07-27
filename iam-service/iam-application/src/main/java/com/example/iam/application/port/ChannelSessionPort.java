package com.example.iam.application.port;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;

import java.util.Set;

/**
 * 渠道会话端口 - 封装 sa-token Token-Session 操作。
 *
 * <p>应用层通过此端口与渠道会话交互,屏蔽具体的安全框架实现细节。
 * iam-adapter 层(或 iam-infrastructure 层)提供具体实现,基于 sa-token 完成会话管理。
 *
 * <p>该端口承担三类职责:
 * <ul>
 *   <li>当前登录上下文查询(渠道类型、用户 ID、用户编号)</li>
 *   <li>计划与权限会话管理(设置/清除/查询当前计划及其权限集合)</li>
 *   <li>登录/登出/踢人下线等会话生命周期操作</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface ChannelSessionPort {

  /**
   * 获取当前登录渠道类型。
   *
   * @return 渠道类型(INTERNET/HQ/BRANCH)
   */
  ChannelType currentChannelType();

  /**
   * 获取当前登录用户 ID。
   *
   * @return 用户 ID
   */
  Long currentUserId();

  /**
   * 获取当前登录用户编号(UserNo)。
   *
   * @return 用户编号
   */
  String currentUserNo();

  /**
   * 设置当前计划与权限到会话(选择计划后调用)。
   *
   * @param planId      计划编号
   * @param permissions 权限码集合
   */
  void setCurrentPlan(String planId, Set<String> permissions);

  /**
   * 清除当前已选计划(切换计划或登出前调用)。
   */
  void clearCurrentPlan();

  /**
   * 获取当前已选计划 ID。
   *
   * @return 计划编号(未选时返回 null)
   */
  String getCurrentPlanId();

  /**
   * 获取当前会话缓存的权限码集合。
   *
   * @return 权限码集合(未选计划时返回空集合)
   */
  Set<String> getCurrentPermissions();

  /**
   * 设置网点二次授权会话信息(经办人确认后调用,柜员借用经办人权限)。
   *
   * @param sessionId   二次授权会话 ID
   * @param approverId  经办人用户 ID
   * @param planId      计划编号
   * @param permissions 借用的权限码集合
   */
  void setSecondaryAuthSession(Long sessionId, Long approverId, String planId, Set<String> permissions);

  /**
   * 清除网点二次授权会话信息(柜员登出或会话过期时调用)。
   */
  void clearSecondaryAuthSession();

  /**
   * 踢用户下线(管理员禁用用户或锁定用户时调用)。
   *
   * @param userId      用户 ID
   * @param channelType 渠道类型
   */
  void kickout(Long userId, ChannelType channelType);

  /**
   * 登录用户(创建会话并返回 token)。
   *
   * @param userId      用户 ID
   * @param channelType 渠道类型
   */
  void login(Long userId, ChannelType channelType);

  /**
   * 登出当前用户(销毁会话)。
   *
   * @param channelType 渠道类型
   */
  void logout(ChannelType channelType);
}
