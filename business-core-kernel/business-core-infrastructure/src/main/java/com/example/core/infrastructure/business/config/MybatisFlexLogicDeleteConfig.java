package com.example.core.infrastructure.business.config;

import com.mybatisflex.core.FlexGlobalConfig;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.logicdelete.impl.BooleanLogicDeleteProcessor;
import com.mybatisflex.spring.boot.MyBatisFlexCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Flex 全局逻辑删除处理器修正（批次 73）。
 *
 * <p>全仓 {@code deleted} 列统一为 BOOLEAN（06-数据库规范），但 MyBatis-Flex 默认的
 * {@code IntegerLogicDeleteProcessor} 会为启用 {@code @Column(isLogicDelete = true)} 的
 * 查询自动追加 {@code deleted = 0}，在真 PostgreSQL 上报
 * {@code operator does not exist: boolean = integer}（H2 宽松转换掩盖了该缺陷，
 * kernel 外部协作恢复任务在真 PG 上持续报错）。
 *
 * <p>注册 {@link BooleanLogicDeleteProcessor} 后自动追加 {@code deleted = FALSE}，
 * 与 PG / H2(PostgreSQL 模式) 的 BOOLEAN 列一致。查询显式携带 deleted 条件时
 * MyBatis-Flex 不重复追加，不受影响。
 */
@Configuration
public class MybatisFlexLogicDeleteConfig implements MyBatisFlexCustomizer {

  @Override
  public void customize(FlexGlobalConfig globalConfig) {
    LogicDeleteManager.setProcessor(new BooleanLogicDeleteProcessor());
  }
}
