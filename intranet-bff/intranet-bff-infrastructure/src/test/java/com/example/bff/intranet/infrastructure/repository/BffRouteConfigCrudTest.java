package com.example.bff.intranet.infrastructure.repository;

import com.example.bff.intranet.infrastructure.TestApplication;
import com.example.bff.shared.route.BffRouteConfig;
import com.example.bff.shared.route.ChannelScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
class BffRouteConfigCrudTest {

    @Autowired
    private BffRouteConfigRepositoryImpl repository;

    @Test
    @DisplayName("save 新增路由配置并返回 ID")
    void save_returnsId() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_A", "annuity-service", ChannelScope.ALL);

        Long id = repository.save(config, "admin");

        assertNotNull(id);
    }

    @Test
    @DisplayName("findById 查询已保存的路由配置")
    void findById_returnsConfig() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_B", "loan-service", ChannelScope.INTRANET);
        Long id = repository.save(config, "admin");

        Optional<BffRouteConfig> result = repository.findById(id);

        assertTrue(result.isPresent());
        assertEquals("CRUD_TYPE_B", result.get().businessType());
        assertEquals("loan-service", result.get().serviceName());
        assertEquals(ChannelScope.INTRANET, result.get().channelScope());
    }

    @Test
    @DisplayName("update 更新路由配置")
    void update_modifiesConfig() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_C", "old-service", ChannelScope.ALL);
        Long id = repository.save(config, "admin");
        BffRouteConfig updated = new BffRouteConfig("CRUD_TYPE_C", "new-service", ChannelScope.INTRANET);

        repository.update(id, updated, "admin");
        Optional<BffRouteConfig> result = repository.findById(id);

        assertTrue(result.isPresent());
        assertEquals("new-service", result.get().serviceName());
        assertEquals(ChannelScope.INTRANET, result.get().channelScope());
    }

    @Test
    @DisplayName("delete 逻辑删除后 findById 返回 empty")
    void delete_softDeletes() {
        BffRouteConfig config = new BffRouteConfig("CRUD_TYPE_D", "some-service", ChannelScope.ALL);
        Long id = repository.save(config, "admin");

        repository.delete(id, "admin");
        Optional<BffRouteConfig> result = repository.findById(id);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findAll 返回未删除的全部路由配置")
    void findAll_returnsAll() {
        repository.save(new BffRouteConfig("CRUD_TYPE_E", "annuity-service", ChannelScope.ALL), "admin");
        repository.save(new BffRouteConfig("CRUD_TYPE_F", "loan-service", ChannelScope.ALL), "admin");

        List<BffRouteConfig> all = repository.findAll();

        assertTrue(all.size() >= 2);
    }
}
