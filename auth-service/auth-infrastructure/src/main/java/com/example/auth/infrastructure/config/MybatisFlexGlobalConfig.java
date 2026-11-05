package com.example.auth.infrastructure.config;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 全局配置（PG 布尔逻辑删除对齐）.
 *
 * <p>实体逻辑删除字段统一为 {@code Boolean deleted}（PG 列类型 BOOLEAN），
 * 而 Flex 默认逻辑删除值为 Integer 0/1——PG 无 boolean=integer 隐式运算符，
 * 真实启动即报错（H2 测试宽松故未暴露）。将逻辑删除值设为
 * {@link Boolean#FALSE}/{@link Boolean#TRUE} 后生成 {@code deleted = false}，与 PG 对齐.
 *
 * <p>其他服务接入真实 PG 启动时需对齐同一配置（实体 deleted 均为 Boolean）.
 *
 * @author auth-infrastructure
 */
@Configuration
public class MybatisFlexGlobalConfig implements MyBatisFlexCustomizer {

  @Override
  public void customize(FlexGlobalConfig globalConfig) {
    globalConfig.setNormalValueOfLogicDelete(Boolean.FALSE);
    globalConfig.setDeletedValueOfLogicDelete(Boolean.TRUE);
  }
}
