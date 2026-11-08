package com.example.file.api;

import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 出站数据增强端点契约（2026-11-08 批次4：设计文档 §7 通用 enrichment 协议）.
 *
 * <p><b>协议由 file-service 定义</b>（零业务语义——file 编译零业务依赖，
 * 端点 URL 由导出模板配置注入，运行时路由回调）：</p>
 *
 * <pre>
 * POST {enricher 端点}
 * 请求: { subTaskId, fileTaskId, baseRows: [ {字段→值}, ... ] }
 * 响应: { code, message, data: [ {字段→增强值}, ... ] }   // 按行一一对应
 * </pre>
 *
 * <p><b>容错语义（设计决策已定稿）</b>：fail-fast——增强调用失败即导出失败，
 * file 侧重试/失败事件由业务决定；金融数据完整性优先，不做降级导出.</p>
 *
 * <p><b>业务接入方式</b>：继承 kernel 的
 * {@code AbstractOutboundEnrichmentEndpoint} 基类，只实现 enrich(rows, keys).</p>
 *
 * @author file-service（契约方）
 */
public interface OutboundDataEnrichmentApi {

  @PostMapping
  ApiResult<List<Map<String, Object>>> enrich(@RequestBody EnrichmentRequest request);

  /**
   * 增强请求（file → 业务）.
   */
  record EnrichmentRequest(
    String subTaskId,
    String fileTaskId,
    List<Map<String, Object>> baseRows
  ) {
  }
}
