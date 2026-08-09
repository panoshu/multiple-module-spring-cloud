package com.example.bff.shared.route;

import java.util.List;
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

    /**
     * 保存路由配置（新增）。
     *
     * @param config       路由配置
     * @param createdBy    创建人
     * @return 生成的 ID
     */
    Long save(BffRouteConfig config, String createdBy);

    /**
     * 更新路由配置。
     *
     * @param id           路由配置 ID
     * @param config       路由配置
     * @param updatedBy    更新人
     */
    void update(Long id, BffRouteConfig config, String updatedBy);

    /**
     * 删除路由配置（逻辑删除）。
     *
     * @param id        路由配置 ID
     * @param updatedBy 更新人
     */
    void delete(Long id, String updatedBy);

    /**
     * 按 ID 查询路由配置。
     *
     * @param id 路由配置 ID
     * @return 路由配置
     */
    Optional<BffRouteConfig> findById(Long id);

    /**
     * 查询全部路由配置（含禁用的，不含已删除的）。
     *
     * @return 路由配置列表
     */
    List<BffRouteConfig> findAll();
}
