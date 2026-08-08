package com.example.bff.shared.route;

import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessTypeRouterTest {

    @Mock
    private BffRouteConfigRepository routeRepo;

    private BusinessTypeRouter router;

    @BeforeEach
    void setUp() {
        router = new BusinessTypeRouter(routeRepo, "INTERNET");
    }

    @Test
    @DisplayName("解析已知业务类型返回正确的服务名")
    void resolveServiceName_returnsServiceName() {
        when(routeRepo.findByBusinessType("ACC_PLAN_CREATE", ChannelScope.INTERNET))
                .thenReturn(Optional.of(new BffRouteConfig("ACC_PLAN_CREATE", "annuity-service", ChannelScope.ALL)));

        String serviceName = router.resolveServiceName("ACC_PLAN_CREATE");

        assertEquals("annuity-service", serviceName);
    }

    @Test
    @DisplayName("相同 businessType 第二次查询走缓存，Repository 只调用一次")
    void resolveServiceName_usesCache() {
        when(routeRepo.findByBusinessType("ACC_PLAN_CREATE", ChannelScope.INTERNET))
                .thenReturn(Optional.of(new BffRouteConfig("ACC_PLAN_CREATE", "annuity-service", ChannelScope.ALL)));

        router.resolveServiceName("ACC_PLAN_CREATE");
        router.resolveServiceName("ACC_PLAN_CREATE");

        verify(routeRepo, times(1)).findByBusinessType("ACC_PLAN_CREATE", ChannelScope.INTERNET);
    }

    @Test
    @DisplayName("未知业务类型抛出 BusinessException")
    void resolveServiceName_unknownType_throwsException() {
        when(routeRepo.findByBusinessType("UNKNOWN_TYPE", ChannelScope.INTERNET))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> router.resolveServiceName("UNKNOWN_TYPE"));
        assertEquals("SERVICE.BFF.0001", ex.code());
    }

    @Test
    @DisplayName("refresh 后缓存清空，Repository 重新被调用")
    void refresh_clearsCache() {
        when(routeRepo.findByBusinessType("ACC_PLAN_CREATE", ChannelScope.INTERNET))
                .thenReturn(Optional.of(new BffRouteConfig("ACC_PLAN_CREATE", "annuity-service", ChannelScope.ALL)));

        router.resolveServiceName("ACC_PLAN_CREATE");
        router.refresh();
        router.resolveServiceName("ACC_PLAN_CREATE");

        verify(routeRepo, times(2)).findByBusinessType("ACC_PLAN_CREATE", ChannelScope.INTERNET);
    }
}
