package com.example.bff.intranet.infrastructure;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * infrastructure 测试专用启动类
 *
 * <p>路由配置 Repository 已抽取至 bff-shared，此处扫描共享的
 * {@code com.example.bff.shared.infrastructure} 包以注册 {@code BffRouteConfigRepositoryImpl}。
 *
 * <p>需 {@code @MapperScan} 显式扫描共享 mapper 包，与项目其他服务保持一致
 * （MyBatis-Flex 自动配置不扫描 {@code @Mapper} 注解接口）。
 *
 * <p>手动声明 {@link DataSource}（{@link DriverManagerDataSource}，无连接池），
 * 因项目未引入 HikariCP，{@code DataSourceAutoConfiguration} 无法自动创建池化数据源。
 * 提供该 Bean 后 {@code MyBatisFlexAutoConfiguration} 自动创建 {@code SqlSessionFactory}，
 * {@code SqlInitializationAutoConfiguration} 自动执行 schema-h2.sql。
 *
 * @author bff
 */
@SpringBootApplication(scanBasePackages = "com.example.bff.shared.infrastructure")
@MapperScan("com.example.bff.shared.infrastructure.mapper")
public class TestApplication {
  public static void main(String[] args) {
    SpringApplication.run(TestApplication.class, args);
  }

  @Bean
  @ConfigurationProperties("spring.datasource")
  public DataSource dataSource() {
    return new DriverManagerDataSource();
  }
}
