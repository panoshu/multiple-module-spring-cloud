/**
 * 跨服务共享的 API 原语（统一响应、分页请求/响应、链路业务标识）。
 *
 * <p><b>S-15 决策</b>：包名保留 {@code com.example.shared.web}，不在此阶段做破坏性重命名——
 * 该模块已作为所有服务 API 的稳定契约被广泛 import，重命名收益低于迁移风险。
 * 新代码仍应只在本模块放置「跨服务传输契约」，不得加入 Web 实现（Controller/Filter/Aspect）。
 *
 * <p>{@link com.example.shared.web.core.annotation.BizTag} 使用面较窄，仅用于
 * shared-web-core 的链路上下文提取；不作为通用业务注解推广。
 */
package com.example.shared.web;
