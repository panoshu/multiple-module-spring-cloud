package com.example.file.infrastructure;

import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * file-infrastructure 集成测试通用启动配置。
 *
 * <p>设计要点：
 * <ul>
 *   <li>不使用 @SpringBootApplication —— 避免级联触发 shared-id-starter /
 *       shared-cache-starter 等无关自动配置；也避免 DataSourceAutoConfiguration
 *       因项目缺少 HikariCP 连接池依赖而失败</li>
 *   <li>使用 @SpringBootConfiguration + 手动 @Bean DataSource/SqlSessionFactory，
 *       参考 {@code JdbcEventStoreTest} 用 DriverManagerDataSource（spring-jdbc 自带，
 *       无需连接池），通过 FlexSqlSessionFactoryBean 创建 MyBatis-Flex 兼容的
 *       SqlSessionFactory</li>
 *   <li>@MapperScan 显式扫描 mapper 包下 BaseMapper 接口注册为 Bean</li>
 *   <li>@ComponentScan 限定范围只加载 repository + converter 包，
 *       避免扫描 storage / gateway 等无关 Bean（依赖 Redis/Kona 等不在测试范围）</li>
 *   <li>表结构通过测试类 @Sql 在每个测试方法前初始化（H2 兼容 DDL）</li>
 * </ul>
 *
 * <p>历史：原 {@code FileAccessLogRepositoryTestConfiguration}（Task 12）仅服务于
 * FileAccessLogRepositoryImplTest；Task 13 新增 FileMetadataTokenRepositoryTest 时
 * 复用同一配置，故重命名为通用命名。
 */
@SpringBootConfiguration
@ComponentScan(basePackages = {
  "com.example.file.infrastructure.repository",
  "com.example.file.infrastructure.converter"
})
@MapperScan("com.example.file.infrastructure.mapper")
@EnableTransactionManagement
public class FileInfrastructureTestConfiguration {

  /**
   * H2 内存数据源（PostgreSQL 兼容模式），不使用连接池。
   *
   * <p>项目主 pom 未引入 HikariCP 等 JDBC 连接池依赖（生产环境靠 spring-boot-starter-jdbc
   * 之外的途径），file-infrastructure 隔离测试时直接用 spring-jdbc 自带的
   * DriverManagerDataSource 即可满足 Repository 集成测试需要。
   */
  @Bean
  public DataSource dataSource() {
    return new DriverManagerDataSource(
      "jdbc:h2:mem:file-infrastructure-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
      "sa",
      ""
    );
  }

  /**
   * MyBatis-Flex SqlSessionFactory，绑定上面的 H2 DataSource。
   *
   * <p>使用 FlexSqlSessionFactoryBean（而非 mybatis-spring 的 SqlSessionFactoryBean）
   * 以启用 MyBatis-Flex 的全部特性（TableDef、QueryWrapper、逻辑删除等）。
   */
  @Bean
  public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
    FlexSqlSessionFactoryBean factoryBean = new FlexSqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);
    return factoryBean.getObject();
  }
}
