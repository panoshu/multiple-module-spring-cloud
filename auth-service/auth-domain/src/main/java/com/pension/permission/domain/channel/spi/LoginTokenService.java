package com.pension.permission.domain.channel.spi;


import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;

import java.util.Optional;

/**
 * 登录态(认证)端口——只负责"这个token是否有效、对应哪个账号、要不要让它失效"，
 * 不涉及任何业务权限判定。基础设施层可以用Sa-Token或者其他任何机制实现，
 * domain/application层完全不知道具体用的是什么组件。
 */
public interface LoginTokenService {

  /**
   * 登录成功后签发token；channel用于区分不同渠道各自的并发登录/踢人策略
   */
  String issueToken(UserNo accountId, AnnuityChannel channel);

  /**
   * 网关/服务侧校验token，返回对应账号；无效或已过期返回empty
   */
  Optional<UserNo> verifyToken(String token);

  /**
   * 登出 / 单个token强制下线
   */
  void invalidateToken(String token);

  /**
   * 账号冻结联动：把这个账号名下所有登录态都踢下线，不只是当前这一个token
   */
  void invalidateAllTokensOf(UserNo accountId);
}
