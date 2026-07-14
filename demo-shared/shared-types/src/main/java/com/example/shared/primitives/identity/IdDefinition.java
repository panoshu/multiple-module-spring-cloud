package com.example.shared.primitives.identity;

import java.lang.annotation.*;

/**
 * 强类型 ID 定义元数据
 * 用于控制 ID 的格式表现和序列生成策略
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface IdDefinition {

  /**
   * ID 生成算法类型
   * 默认使用号段模式 (保持向下兼容)
   */
  IdType type() default IdType.SEGMENT;

  /**
   * ID 基础名称 (Base Name)
   * 数据来源: 静态配置
   * 作用: 作为 %n 的替换值，参与序列 Key 的计算
   * 默认值: 空字符串 (表示使用 "类名去掉Id后缀的大写形式"，如 BatchId -> BATCH)
   */
  String name() default "";

  /**
   * 【显示格式】ID 最终字符串模板
   * 决定 ID 给用户看到的样子
   * * 占位符说明:
   * %p - 业务前缀 (Prefix): 运行时传入，如 "LOAN"
   * %d - 日期时间 (Date): 当前时间，格式由 dateFormat() 定义
   * %s - 序列号 (Sequence): 由 TinyId 生成并补零后的数字
   * * 示例: "%p%d%s" -> "LOAN20260106000001"
   */
  String format() default "%p%d%s";

  /**
   * 【序列策略】TinyId 序列键模板
   * 决定使用哪个计数器，从而控制序列的隔离级别和重置周期
   * * 占位符说明:
   * %n - 基础名称 (Name): 对应 name() 属性
   * %p - 业务前缀 (Prefix): 运行时传入
   * %d - 日期时间 (Date): 通常用于实现"日切" (每天序列归零)
   * * 常见策略:
   * 1. "%n_%p" (默认): 按业务隔离。例如 "BATCH_LOAN", "BATCH_PAY" 互不干扰。
   * 2. "%n": 全局共享。所有业务共用一个计数器。
   * 3. "%n_%d": 按天重置，全局共享。例如 "BATCH_20260106"。
   * 4. "%n_%p_%d": 按天重置，按业务隔离。例如 "BATCH_LOAN_20260106"。
   */
  String seqKey() default "%n_%p";

  /**
   * 日期格式
   * 作用于 format() 和 seqKey() 中的 %d 占位符
   */
  String dateFormat() default "yyyyMMdd";

  /**
   * 序列长度 (最小位数)
   * 不足此长度时左侧补 0
   * 示例: seqLength=6, 序号=5 -> "000005"
   */
  int seqLength() default 6;
}
