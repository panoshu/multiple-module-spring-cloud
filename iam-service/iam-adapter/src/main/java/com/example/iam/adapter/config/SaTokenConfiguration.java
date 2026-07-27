package com.example.iam.adapter.config;

import com.example.iam.adapter.security.StpBranchUtil;
import com.example.iam.adapter.security.StpHqUtil;
import com.example.iam.adapter.security.StpInternetUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * sa-token 多 StpLogic 集成配置。
 *
 * <p>启动时初始化三套渠道(INTERNET/HQ/BRANCH)的 StpLogic 实例,配置:
 * <ul>
 *   <li>每渠道独立的 Token Header 名称(satoken-internet / satoken-hq / satoken-branch)</li>
 *   <li>渠道级会话参数(timeout / active-timeout / is-concurrent)从 {@link IamChannelProperties} 读取</li>
 * </ul>
 *
 * <p>本配置通过 {@link PostConstruct} 在 Bean 初始化后设置静态 StpLogic 实例的 Token Header,
 * 确保 sa-token 在读取请求 Header 时能按渠道区分。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(IamChannelProperties.class)
@RequiredArgsConstructor
public class SaTokenConfiguration {

  private final IamChannelProperties channelProperties;

  /**
   * 初始化三渠道 StpLogic 的 Token Header 与会话参数。
   *
   * <p>sa-token 的 StpLogic 在读取请求 Token 时,通过 {@code getTokenName()} 获取 Header 名称。
   * 本方法通过设置各 StpLogic 的 config,使其从渠道专属 Header 读取 Token。
   */
  @PostConstruct
  public void initStpLogics() {
    configureChannel(StpInternetUtil.stpLogic, StpInternetUtil.TOKEN_NAME,
        channelProperties.getChannels().get(StpInternetUtil.TYPE));
    configureChannel(StpHqUtil.stpLogic, StpHqUtil.TOKEN_NAME,
        channelProperties.getChannels().get(StpHqUtil.TYPE));
    configureChannel(StpBranchUtil.stpLogic, StpBranchUtil.TOKEN_NAME,
        channelProperties.getChannels().get(StpBranchUtil.TYPE));

    log.info("IAM sa-token 多 StpLogic 初始化完成: channels={}, secondaryAuth.sessionTimeout={}s, "
            + "permission.combinationStrategy={}",
        channelProperties.getChannels().keySet(),
        channelProperties.getSecondaryAuth().getSessionTimeout(),
        channelProperties.getPermission().getCombinationStrategy());
  }

  /**
   * 配置单个渠道 StpLogic 的 Token Header 名称与会话参数。
   *
   * <p>注意:sa-token 1.45 的 StpLogic 通过 {@code getConfig().getTokenName()} 获取 Header 名。
   * 此处通过设置自定义 config 实现每渠道独立 Header,并显式声明 is-read-header/is-read-cookie
   * 避免使用默认值导致 Cookie 读取泄漏(对应设计文档 4.2.1 节安全约束)。
   *
   * <p>配置覆盖顺序(从低到高):
   * <ol>
   *   <li>全局 sa-token 配置(application.yml 中的 sa-token.*)</li>
   *   <li>渠道级配置(iam.security.channels.xxx,通过本方法覆盖)</li>
   *   <li>登录时 SaLoginModel(覆盖单次登录行为)</li>
   * </ol>
   *
   * @param stpLogic     StpLogic 实例
   * @param tokenName    Token Header 名称
   * @param channelConfig 渠道级配置(可为 null)
   */
  private void configureChannel(cn.dev33.satoken.stp.StpLogic stpLogic,
                                  String tokenName,
                                  IamChannelProperties.ChannelConfig channelConfig) {
    // 创建渠道级配置,继承全局配置后覆盖 Token Header 名及会话参数
    cn.dev33.satoken.config.SaTokenConfig config = new cn.dev33.satoken.config.SaTokenConfig();
    config.setTokenName(tokenName);
    // 显式声明从 Header 读取(前后台分离)避免依赖默认值
    config.setIsReadHeader(true);
    // 显式关闭 Cookie 读取以避免 CSRF 风险
    config.setIsReadCookie(false);
    if (channelConfig != null) {
      config.setTimeout(channelConfig.getTimeout());
      config.setActiveTimeout(channelConfig.getActiveTimeout());
      config.setIsConcurrent(channelConfig.isConcurrent());
    }
    // 设置为该 StpLogic 的专属配置
    stpLogic.setConfig(config);
  }
}
