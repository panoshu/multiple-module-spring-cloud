package com.example.file.domain.gateway;

import java.util.List;
import java.util.Map;

/**
 * 出站数据增强网关 SPI（2026-11-08 批次5：设计文档 §7 enricher 接线）.
 *
 * <p>导出生成流程在模板填充前回调业务服务的增强端点（enriched 字段实时计算，
 * 数据以暂存区为单一事实源、增强在导出瞬间取业务最新值）——端点 URL 由
 * 导出模板配置注入（运行时路由，file 编译零业务依赖）.</p>
 *
 * <p><b>容错语义（设计已定稿）</b>：fail-fast——增强调用失败即导出失败，
 * 由调用方决定重试；不降级导出（金融数据完整性优先）.</p>
 *
 * @author file-service
 */
public interface OutboundEnricherGateway {

  /**
   * 调用业务增强端点.
   *
   * @param endpoint  增强端点 URL（模板配置注入）
   * @param subTaskId 子任务凭证
   * @param fileTaskId 文件任务凭证
   * @param baseRows  解析原始数据行（字段域 base 字段）
   * @return 增强行集（与 baseRows 一一对应；由字段域 merge 规则合并——enriched 覆盖 base）
   */
  List<Map<String, Object>> invoke(String endpoint, String subTaskId, String fileTaskId,
                                    List<Map<String, Object>> baseRows);
}
