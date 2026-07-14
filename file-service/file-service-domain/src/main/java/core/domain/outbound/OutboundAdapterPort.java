package core.domain.outbound;

import core.domain.model.ExportEngineType;
import core.domain.model.OutboundTemplate;

import java.io.OutputStream;

/**
 * OutboundAdapterPort
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/24 22:38
 */
public interface OutboundAdapterPort {
  /**
   * 声明该适配器支持哪种引擎类型
   */
  boolean supports(ExportEngineType engineType);

  /**
   * 执行真正的渲染写入
   */
  void write(OutboundTemplate template, String jsonPayload, OutputStream outputStream);
}
