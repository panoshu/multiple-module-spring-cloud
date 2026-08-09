package com.example.bff.intranet.infrastructure.repository;

import com.example.bff.intranet.infrastructure.entity.BffRouteConfigDO;
import com.example.bff.intranet.infrastructure.mapper.BffRouteConfigMapper;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.BffRouteConfigRepository;
import com.example.bff.shared.route.ChannelScope;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static com.example.bff.intranet.infrastructure.entity.table.BffRouteConfigDOTableDef.BFF_ROUTE_CONFIG_DO;

/**
 * BFF 路由配置 Repository 实现
 *
 * <p>查询逻辑：优先匹配指定渠道，未找到则回退到 ALL。
 *
 * @author bff
 */
@Repository
public class BffRouteConfigRepositoryImpl implements BffRouteConfigRepository {

    private final BffRouteConfigMapper mapper;

    public BffRouteConfigRepositoryImpl(BffRouteConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<BffRouteConfig> findByBusinessType(String businessType, ChannelScope channelScope) {
        BffRouteConfigDO record = mapper.selectOneByQuery(
                QueryWrapper.create()
                        .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
                        .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(channelScope.name()))
                        .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        if (record == null) {
            record = mapper.selectOneByQuery(
                    QueryWrapper.create()
                            .where(BFF_ROUTE_CONFIG_DO.BUSINESS_TYPE.eq(businessType))
                            .and(BFF_ROUTE_CONFIG_DO.CHANNEL_SCOPE.eq(ChannelScope.ALL.name()))
                            .and(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                            .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
            );
        }
        return Optional.ofNullable(record).map(this::toRouteConfig);
    }

    @Override
    public Set<String> findAllServiceNames() {
        java.util.List<BffRouteConfigDO> records = mapper.selectListByQuery(
                QueryWrapper.create()
                        .select(BFF_ROUTE_CONFIG_DO.SERVICE_NAME)
                        .where(BFF_ROUTE_CONFIG_DO.ENABLED.eq(true))
                        .and(BFF_ROUTE_CONFIG_DO.DELETED.eq(false))
        );
        Set<String> names = new LinkedHashSet<>();
        for (BffRouteConfigDO record : records) {
            names.add(record.getServiceName());
        }
        return names;
    }

    private BffRouteConfig toRouteConfig(BffRouteConfigDO record) {
        return new BffRouteConfig(
                record.getBusinessType(),
                record.getServiceName(),
                ChannelScope.valueOf(record.getChannelScope())
        );
    }
}
