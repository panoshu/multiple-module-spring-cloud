package com.example.bff.shared.route;

import java.util.Optional;
import java.util.Set;

/**
 * BFF 路由配置 Repository 接口
 *
 * <p>由各 BFF 的 infrastructure 层实现（MyBatis-Flex）。
 *
 * @author bff
 */
public interface BffRouteConfigRepository {

    /**
     * 按业务类型查找路由配置。
     *
     * <p>优先匹配指定渠道，未找到则回退到 ALL。
     *
     * @param businessType 业务类型
     * @param channelScope 渠道范围
     * @return 路由配置
     */
    Optional<BffRouteConfig> findByBusinessType(String businessType, ChannelScope channelScope);

    /**
     * 获取所有已配置的服务名（去重）。
     *
     * @return 服务名集合
     */
    Set<String> findAllServiceNames();
}
