package com.example.bff.intranet;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.auth.api.CustomerChannelEntitlementApi;
import com.example.auth.api.PermissionCacheApi;
import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.PermissionMetadataApi;
import com.example.bff.shared.infrastructure.repository.BffRouteConfigRepositoryImpl;
import com.example.bff.shared.registry.KernelApiRegistry;
import com.example.bff.shared.route.BusinessTypeRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内网/专线 BFF 启动模块集成测试
 *
 * <p>验证 Spring 上下文加载、BFF 核心组件（BusinessTypeRouter / KernelApiRegistry /
 * BffRouteConfigRepositoryImpl）注册、全部 6 个 Controller 注册。
 *
 * <p>外部 @HttpExchange API 客户端通过 {@link MockitoBean} 注入空实现，避免真实 HTTP 调用
 * （测试环境不配置 {@code httpexchange.clients}，autoconfigure 不会创建代理 Bean）。
 *
 * <p>测试环境通过嵌套 {@link TestInfrastructureConfiguration} 提供：
 * <ul>
 *   <li>{@link DataSource}（{@link DriverManagerDataSource}，无连接池）：intranet-bff-starter
 *       依赖链不含 HikariCP（不依赖 business-core-infrastructure），{@code DataSourceAutoConfiguration}
 *       无法自动创建池化数据源，导致 MyBatis-Flex 的 {@code SqlSessionFactory} 缺失。</li>
 *   <li>{@code @LoadBalanced RestClient.Builder}：测试环境无注册中心，Spring Cloud LoadBalancer
 *       自动配置链不会注册带 {@code @LoadBalanced} 限定的 {@link RestClient.Builder} bean，
 *       手动提供一个不实际执行负载均衡的 builder 仅为满足 {@link KernelApiRegistry} 的依赖注入。</li>
 * </ul>
 *
 * @author bff
 */
@SpringBootTest
class IntranetBffApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private BusinessTypeRouter businessTypeRouter;

    @Autowired
    private KernelApiRegistry kernelApiRegistry;

    @Autowired
    private BffRouteConfigRepositoryImpl routeConfigRepository;

    // 外部 @HttpExchange API 的 mock 实现，避免真实 HTTP 调用
    @MockitoBean
    private ApprovalFlowApi approvalFlowApi;
    @MockitoBean
    private ApprovalInstanceApi approvalInstanceApi;
    @MockitoBean
    private PermissionCheckApi permissionCheckApi;
    @MockitoBean
    private PermissionMetadataApi permissionMetadataApi;
    @MockitoBean
    private PermissionCacheApi permissionCacheApi;
    @MockitoBean
    private CustomerChannelEntitlementApi customerChannelEntitlementApi;

    @Test
    @DisplayName("应用上下文加载成功")
    void contextLoads() {
        assertNotNull(applicationContext);
    }

    @Test
    @DisplayName("BFF 核心组件已注册")
    void coreComponentsRegistered() {
        assertNotNull(businessTypeRouter);
        assertNotNull(kernelApiRegistry);
        assertNotNull(routeConfigRepository);
    }

    @Test
    @DisplayName("BffBusinessController 已注册")
    void bffBusinessControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffBusinessController"));
    }

    @Test
    @DisplayName("BffRouteManagementController 已注册")
    void bffRouteManagementControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffRouteManagementController"));
    }

    @Test
    @DisplayName("BffApprovalFlowController 已注册")
    void bffApprovalFlowControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffApprovalFlowController"));
    }

    @Test
    @DisplayName("BffApprovalInstanceController 已注册")
    void bffApprovalInstanceControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffApprovalInstanceController"));
    }

    @Test
    @DisplayName("BffPermissionController 已注册")
    void bffPermissionControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffPermissionController"));
    }

    @Test
    @DisplayName("BffChannelController 已注册")
    void bffChannelControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffChannelController"));
    }

    @Test
    @DisplayName("BffSystemController 已注册")
    void bffSystemControllerRegistered() {
        assertTrue(applicationContext.containsBean("bffSystemController"));
    }

    /**
     * 测试环境基础设施 bean 配置
     *
     * <p>提供 DataSource 与 {@code @LoadBalanced RestClient.Builder} 两个测试专用 bean，
     * 以填补 intranet-bff-starter 依赖链相对其他业务服务缺少的自动配置（HikariCP / 实际 LoadBalancer）。
     */
    @TestConfiguration
    static class TestInfrastructureConfiguration {

        @Bean
        @ConfigurationProperties("spring.datasource")
        public DataSource dataSource() {
            return new DriverManagerDataSource();
        }

        @Bean
        @LoadBalanced
        public RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }
}
