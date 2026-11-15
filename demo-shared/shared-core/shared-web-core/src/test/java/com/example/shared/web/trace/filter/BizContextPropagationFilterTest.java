package com.example.shared.web.trace.filter;

import com.example.shared.web.trace.config.TraceContextProperties;
import com.example.shared.web.trace.impl.MdcBizContextAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("V-05 链路 traceId 入站生成与透传")
class BizContextPropagationFilterTest {

  private TraceContextProperties properties;
  private BizContextPropagationFilter filter;

  @BeforeEach
  void setUp() {
    properties = new TraceContextProperties();
    properties.setMapping(Map.of("OrderId", "X-Order-Id"));
    filter = new BizContextPropagationFilter(properties, new MdcBizContextAccessor());
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("复用上游 X-Trace-Id，并写入 MDC 与响应头")
  void reusesUpstreamTraceId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");
    request.addHeader("X-Trace-Id", "trace-abc_123");
    request.addHeader("X-Order-Id", "ORD-1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> traceInChain = new AtomicReference<>();
    AtomicReference<String> orderInChain = new AtomicReference<>();

    filter.doFilter(request, response, (req, res) -> {
      traceInChain.set(MDC.get(BizContextPropagationFilter.TRACE_ID_MDC_KEY));
      orderInChain.set(MDC.get("OrderId"));
    });

    assertThat(traceInChain.get()).isEqualTo("trace-abc_123");
    assertThat(orderInChain.get()).isEqualTo("ORD-1");
    assertThat(response.getHeader("x-trace-id")).isEqualTo("trace-abc_123");
    assertThat(response.getHeader("trace-id")).isEqualTo("trace-abc_123");
    assertThat(MDC.get(BizContextPropagationFilter.TRACE_ID_MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("缺少 traceId 时生成 32 位十六进制 ID")
  void generatesTraceIdWhenMissing() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> traceInChain = new AtomicReference<>();

    filter.doFilter(request, response, (req, res) ->
      traceInChain.set(MDC.get(BizContextPropagationFilter.TRACE_ID_MDC_KEY)));

    assertThat(traceInChain.get()).matches("[0-9a-f]{32}");
    assertThat(response.getHeader("x-trace-id")).isEqualTo(traceInChain.get());
  }

  @Test
  @DisplayName("非法 traceId 不被信任，重新生成")
  void regeneratesInvalidTraceId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo");
    request.addHeader("X-Trace-Id", "bad trace id!");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> traceInChain = new AtomicReference<>();

    filter.doFilter(request, response, (req, res) ->
      traceInChain.set(MDC.get(BizContextPropagationFilter.TRACE_ID_MDC_KEY)));

    assertThat(traceInChain.get()).matches("[0-9a-f]{32}").isNotEqualTo("bad trace id!");
    assertThat(response.getHeader("x-trace-id")).isEqualTo(traceInChain.get());
  }

  @Test
  @DisplayName("宿主覆盖业务头 mapping 后仍保留 traceId 保留别名")
  void keepsReservedTraceIdMapping() {
    assertThat(properties.getHeaderKey(BizContextPropagationFilter.TRACE_ID_MDC_KEY))
      .isEqualTo("x-trace-id");
    assertThat(properties.getAlias("X-Trace-Id"))
      .isEqualTo(BizContextPropagationFilter.TRACE_ID_MDC_KEY);
    assertThat(properties.getAliases()).contains("OrderId");
  }
}
