package com.example.gateway.security;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.context.model.SaRequest;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 渠道感知路由器 - 基于请求路径前缀分派到对应渠道的 StpLogic。
 *
 * <p>对应设计文档 4.5 节:demo-gateway 的 SaReactorFilter 通过本组件完成:
 * <ol>
 *   <li>渠道识别:根据请求路径前缀(/internet, /hq, /branch)确定渠道类型</li>
 *   <li>登录校验:调用对应渠道 StpLogic.checkLogin()</li>
 *   <li>权限/角色校验:供 SaTokenGatewayConfiguration 在路由规则匹配时回调</li>
 * </ol>
 *
 * <p>每个渠道独立持有一个 {@link StpLogic} 实例,通过 {@link SaTokenConfig#setTokenName}
 * 设置渠道专属 Token Header 名(satoken-internet / satoken-hq / satoken-branch),
 * 实现 Token 互不干扰,便于独立管理。
 *
 * <p>sa-token reactor 模式下,SaReactorFilter 在 setAuth 回调前已通过
 * SaHolder 设置好请求/响应上下文,StpLogic.checkLogin 等同步 API 可直接调用。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Component
public class ChannelAwareSaRouter {

  private final Map<ChannelType, StpLogic> stpLogicMap;

  public ChannelAwareSaRouter() {
    this.stpLogicMap = new EnumMap<>(ChannelType.class);
    for (ChannelType channel : ChannelType.values()) {
      StpLogic logic = new StpLogic(channel.loginType());
      SaTokenConfig config = new SaTokenConfig();
      config.setTokenName(channel.tokenHeader());
      // 显式声明从 Header 读取(前后台分离),与 iam-service SaTokenConfiguration 保持一致
      config.setIsReadHeader(true);
      // 显式关闭 Cookie 读取以避免 CSRF 风险(对应设计文档 4.2.2 节安全约束)
      config.setIsReadCookie(false);
      logic.setConfig(config);
      stpLogicMap.put(channel, logic);
    }
    log.info("ChannelAwareSaRouter 初始化完成: channels={}",
      stpLogicMap.keySet().stream().map(ChannelType::loginType).toList());
  }

  /**
   * 根据请求路径识别渠道并校验登录。
   *
   * <p>调用时机:SaReactorFilter.setAuth 回调入口。
   * <ul>
   *   <li>路径匹配渠道前缀(如 /internet/**):调用对应 StpLogic.checkLogin,
   *       未登录抛 NotLoginException 由 setError 处理</li>
   *   <li>路径不匹配任何渠道前缀(如 /actuator/**, /favicon.ico):
   *       返回 null,表示公共接口,跳过登录校验</li>
   * </ul>
   *
   * @return 命中的渠道类型;公共接口返回 null
   */
  public ChannelType matchAndCheckLogin() {
    SaRequest request = SaHolder.getRequest();
    String path = request.getRequestPath();
    ChannelType channel = ChannelType.fromPath(path);
    if (channel == null) {
      return null;
    }
    StpLogic stpLogic = stpLogicMap.get(channel);
    stpLogic.checkLogin();
    return channel;
  }

  /**
   * 获取指定渠道的 StpLogic 实例。
   *
   * <p>供 SaTokenGatewayConfiguration 在路由规则匹配后调用
   * checkPermission / checkRole 等方法。
   *
   * @param channel 渠道类型
   * @return 对应渠道的 StpLogic
   */
  public StpLogic getStpLogic(ChannelType channel) {
    return stpLogicMap.get(channel);
  }

  /**
   * 根据 loginType 获取对应渠道类型。
   *
   * @param loginType sa-token loginType 标识(internet/hq/branch)
   * @return 对应的渠道类型;未知返回 null
   */
  public ChannelType getChannelByLoginType(String loginType) {
    for (ChannelType channel : ChannelType.values()) {
      if (channel.loginType().equals(loginType)) {
        return channel;
      }
    }
    return null;
  }

  /**
   * 仅根据请求路径识别渠道，不校验登录.
   *
   * <p>供 SaTokenGatewayConfiguration 在 setAuth 中调用，由调用方决定是否调用 checkLogin。
   *
   * @param path 请求路径
   * @return 命中的渠道类型；非渠道前缀返回 null
   */
  public ChannelType matchChannel(String path) {
    return ChannelType.fromPath(path);
  }

  /**
   * 配置默认 StpLogic 识别所有渠道 token.
   *
   * <p>管理类 API（非渠道前缀路径）使用默认 StpLogic 校验登录态，
   * 默认 StpLogic 读取所有三个渠道的 Header，用户携带任一渠道 token 都能通过校验。
   *
   * <p>sa-token 1.45.0 的 {@link SaTokenConfig#setTokenName(String)} 仅支持单个 token 名称，
   * 因此通过覆写 {@link StpLogic#getTokenValueNotCut()} 遍历所有渠道 Header 实现多 Header 识别。
   *
   * <p>在 SaTokenGatewayConfiguration 初始化时调用一次。
   */
  public void configureDefaultStpLogic() {
    List<String> channelHeaders = List.of(
        ChannelType.INTERNET.tokenHeader(),
        ChannelType.HQ.tokenHeader(),
        ChannelType.BRANCH.tokenHeader());
    StpLogic defaultLogic = new StpLogic("default") {
      @Override
      public String getTokenValueNotCut() {
        SaRequest request = SaHolder.getRequest();
        for (String header : channelHeaders) {
          String tokenValue = request.getHeader(header);
          if (tokenValue != null && !tokenValue.isEmpty()) {
            return tokenValue;
          }
        }
        return null;
      }
    };
    SaTokenConfig config = new SaTokenConfig();
    config.setIsReadHeader(true);
    config.setIsReadCookie(false);
    defaultLogic.setConfig(config);
    StpUtil.setStpLogic(defaultLogic);
    log.info("默认 StpLogic 配置完成: 识别所有渠道 token: {}", channelHeaders);
  }
}
