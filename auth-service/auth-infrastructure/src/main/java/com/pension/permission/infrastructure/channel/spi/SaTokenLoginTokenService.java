package com.pension.permission.infrastructure.channel.spi;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 基于 Sa-Token 的 {@link LoginTokenService} SPI 实现.
 *
 * <p>利用 Sa-Token 提供的 token 签发、校验、登出、踢人下线能力，
 * 配合 {@code sa-token-redis-template} 实现分布式会话管理。</p>
 *
 * <h3>与 Session 聚合根的关系</h3>
 * <p>本类仅管理"登录态"——即 token 是否有效、对应哪个账号。
 * 业务上下文（有效身份、已选计划、二次授权绑定）由 {@code Session} 聚合根承载，
 * 存储于 Redis（见 {@code SessionRepositoryImpl}）。</p>
 *
 * <p>{@code Session.id} = Sa-Token 的 tokenValue，二者合一，
 * 使得"给定 token 找会话"无需额外映射表。</p>
 *
 * <h3>多渠道并发登录</h3>
 * <p>依赖 {@code application.yml} 中 {@code sa-token.is-concurrent=true} 配置，
 * 同一账号在互联网/网点/总部三渠道可并发登录，每次签发独立 token。
 * 账号冻结联动调用 {@link #invalidateAllTokensOf(UserNo)} 会把该账号所有渠道 token 一并踢下线。</p>
 */
@Slf4j
@Component
public class SaTokenLoginTokenService implements LoginTokenService {

  @Override
  public String issueToken(UserNo accountId, AnnuityChannel channel) {
    if (accountId == null || channel == null) {
      throw new IllegalArgumentException("accountId 和 channel 不能为空");
    }

    // 签发 token：StpUtil.login 会创建登录会话并存储到 Redis（sa-token-redis-template）
    // channel 信息由 Session 聚合根自身承载，这里仅签发 token，不绑定 device
    StpUtil.login(accountId.value());
    String tokenValue = StpUtil.getTokenValue();

    log.debug("签发 token: accountId={}, channel={}", accountId.value(), channel);
    return tokenValue;
  }

  @Override
  public Optional<UserNo> verifyToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }

    try {
      // Sa-Token 在 logout/kickout 后会从 Redis 删除 token-session 映射，
      // getLoginIdByToken 返回 null 即代表 token 已失效（过期、登出或被踢下线）
      Object loginId = StpUtil.getLoginIdByToken(token);
      if (loginId == null) {
        return Optional.empty();
      }
      return Optional.of(UserNo.of(loginId.toString()));
    } catch (NotLoginException e) {
      return Optional.empty();
    }
  }

  @Override
  public void invalidateToken(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    StpUtil.logoutByTokenValue(token);
    log.debug("登出 token: tokenLength={}", token.length());
  }

  @Override
  public void invalidateAllTokensOf(UserNo accountId) {
    if (accountId == null) {
      return;
    }
    // kickout 会把该账号名下所有 token 标记为强制下线，
    // 包括所有渠道（互联网/网点/总部）的登录态
    StpUtil.kickout(accountId.value());
    log.debug("账号强制下线: accountId={}", accountId.value());
  }
}
