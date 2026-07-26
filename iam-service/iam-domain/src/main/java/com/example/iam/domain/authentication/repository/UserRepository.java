package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.types.UserId;
import com.example.shared.domain.repository.Repository;

import java.util.Optional;

/**
 * 用户聚合根仓储接口。
 *
 * <p>定义用户领域的查询语义,实现位于 {@code iam-infrastructure} 层。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface UserRepository extends Repository<User, UserId> {

  /**
   * 根据登录名查找用户(用于登录验证)。
   *
   * @param loginName   登录名
   * @param channelType 渠道类型
   * @return 用户(可能为空)
   */
  Optional<User> findByLoginName(String loginName, ChannelType channelType);

  /**
   * 检查登录名是否已存在(用于创建时唯一性校验)。
   *
   * @param loginName   登录名
   * @param channelType 渠道类型
   * @return 存在返回 true
   */
  boolean existsByLoginName(String loginName, ChannelType channelType);
}
