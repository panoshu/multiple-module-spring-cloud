package com.pension.permission.infrastructure.channel.spi;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 基于 Sa-Token 的 {@link LoginTokenService} SPI 实现.
 *
 * <p>使用渠道对应的 StpLogic 实例签发/校验 token，确保签发的 token
 * 与网关侧 {@code ChannelAwareSaRouter} 的 StpLogic 使用相同的 loginType，
 * 从而网关能正确识别 token。</p>
 */
@Slf4j
@Component
public class SaTokenLoginTokenService implements LoginTokenService {

  private final Map<AnnuityChannel, StpLogic> channelStpLogicMap;

  public SaTokenLoginTokenService(Map<AnnuityChannel, StpLogic> channelStpLogicMap) {
    this.channelStpLogicMap = channelStpLogicMap;
  }

  @Override
  public String issueToken(UserNo accountId, AnnuityChannel channel) {
    if (accountId == null || channel == null) {
      throw new IllegalArgumentException("accountId 和 channel 不能为空");
    }
    StpLogic stpLogic = resolveStpLogic(channel);
    stpLogic.login(accountId.value());
    String tokenValue = stpLogic.getTokenValue();

    log.debug("签发 token: accountId={}, channel={}, loginType={}", accountId.value(), channel, stpLogic.getLoginType());
    return tokenValue;
  }

  @Override
  public Optional<UserNo> verifyToken(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    for (Map.Entry<AnnuityChannel, StpLogic> entry : channelStpLogicMap.entrySet()) {
      try {
        Object loginId = entry.getValue().getLoginIdByToken(token);
        if (loginId != null) {
          return Optional.of(UserNo.of(loginId.toString()));
        }
      } catch (NotLoginException ignored) {
        // 该渠道不认识此 token，继续尝试
      }
    }
    return Optional.empty();
  }

  @Override
  public void invalidateToken(String token) {
    if (token == null || token.isBlank()) {
      return;
    }
    for (StpLogic stpLogic : channelStpLogicMap.values()) {
      try {
        stpLogic.logoutByTokenValue(token);
      } catch (Exception ignored) {
        // 该渠道不认识此 token，继续尝试
      }
    }
    log.debug("登出 token: tokenLength={}", token.length());
  }

  @Override
  public void invalidateAllTokensOf(UserNo accountId) {
    if (accountId == null) {
      return;
    }
    for (StpLogic stpLogic : channelStpLogicMap.values()) {
      try {
        stpLogic.kickout(accountId.value());
      } catch (Exception ignored) {
        // 该渠道可能无此账号登录态
      }
    }
    log.debug("账号强制下线: accountId={}", accountId.value());
  }

  /**
   * 获取渠道对应的 token header 名称（供 Controller 返回给前端）.
   */
  public String getTokenName(AnnuityChannel channel) {
    StpLogic stpLogic = resolveStpLogic(channel);
    return stpLogic.getConfigOrGlobal().getTokenName();
  }

  private StpLogic resolveStpLogic(AnnuityChannel channel) {
    StpLogic stpLogic = channelStpLogicMap.get(channel);
    if (stpLogic == null) {
      throw new IllegalArgumentException("未注册该渠道的 StpLogic: " + channel);
    }
    return stpLogic;
  }
}
