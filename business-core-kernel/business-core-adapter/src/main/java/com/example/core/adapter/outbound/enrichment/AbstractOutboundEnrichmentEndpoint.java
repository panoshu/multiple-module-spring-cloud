package com.example.core.adapter.outbound.enrichment;

import com.example.file.api.OutboundDataEnrichmentApi;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 出站数据增强端点基类（2026-11-08 批次4：设计文档 §7 三层中的"骨架层"）.
 *
 * <p><b>三层分工</b>：</p>
 * <ul>
 *   <li>协议：file-service 定义（{@link OutboundDataEnrichmentApi}，零业务语义）</li>
 *   <li>骨架：本基类（kernel 提供封装——协议实现/空值防御/异常规范）</li>
 *   <li>逻辑：业务服务继承本类，只实现 {@link #enrich}（查业务库+计算）</li>
 * </ul>
 *
 * <p>端点 URL 由导出模板配置注入（file 侧运行时路由回调，编译零依赖）.
 * 增强结果须与 baseRows 按行一一对应（行数不一致视为协议错误——fail-fast）.</p>
 *
 * <p><b>使用示例</b>：</p>
 * <pre>{@code
 * @RestController
 * public class AnnuityOutboundEnrichmentEndpoint extends AbstractOutboundEnrichmentEndpoint {
 *   protected Map<String, Object> enrichRow(Map<String, Object> baseRow, EnrichmentKeys keys) {
 *     // 查业务库 + 业务计算，返回增强字段（key 须在字段域的 enriched 声明内）
 *     return Map.of("bizRiskLevel", riskService.evaluate(baseRow));
 *   }
 * }
 * }</pre>
 *
 * @author business-core-kernel
 */
public abstract class AbstractOutboundEnrichmentEndpoint implements OutboundDataEnrichmentApi {

  @Override
  public final ApiResult<List<Map<String, Object>>> enrich(@RequestBody EnrichmentRequest request) {
    if (request.baseRows() == null || request.baseRows().isEmpty()) {
      return ApiResult.success(List.of());
    }
    List<Map<String, Object>> enriched = request.baseRows().stream()
      .map(row -> enrichRow(row, new EnrichmentKeys(request.subTaskId(), request.fileTaskId())))
      .toList();
    if (enriched.size() != request.baseRows().size()) {
      // 协议违约（行数不对应）——fail-fast（设计决策：金融数据完整性优先）
      throw new IllegalStateException("enrichment 行数与 baseRows 不一致: "
        + enriched.size() + " vs " + request.baseRows().size());
    }
    return ApiResult.success(enriched);
  }

  /**
   * 单行增强——业务实现（返回增强字段集；空 Map 表示该行无增强）.
   */
  protected abstract Map<String, Object> enrichRow(Map<String, Object> baseRow, EnrichmentKeys keys);

  /**
   * 增强上下文凭证.
   */
  public record EnrichmentKeys(String subTaskId, String fileTaskId) {
  }
}
