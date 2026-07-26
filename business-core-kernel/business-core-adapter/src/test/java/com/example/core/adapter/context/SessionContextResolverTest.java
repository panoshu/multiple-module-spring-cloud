package com.example.core.adapter.context;

import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SessionContextResolver 单元测试
 *
 * @author panoshu
 */
class SessionContextResolverTest {

    private SessionContextResolver resolver;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        resolver = new SessionContextResolver(objectMapper);
    }

    @Test
    void should_resolve_session_context_from_header() throws Exception {
        SessionContext session = new SessionContext(
            "U001", "USER", "alice", "Alice",
            "INTERNET", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            true, "U002", "bob",
            false, null, null,
            Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of("P001")
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        String json = objectMapper.writeValueAsString(session);
        request.addHeader("X-Session-Context", Base64.getEncoder().encodeToString(json.getBytes()));

        SessionContext resolved = resolver.require(request);

        assertThat(resolved).isEqualTo(session);
        assertThat(resolved.userNo()).isEqualTo("U001");
        assertThat(resolved.channelType()).isEqualTo("INTERNET");
        assertThat(resolved.isProxy()).isTrue();
    }

    @Test
    void should_throw_when_header_missing_on_require() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> resolver.require(request))
            .isInstanceOf(BusinessException.class)
            .extracting(throwable -> ((BusinessException) throwable).displayMessage())
            .asString()
            .contains("会话上下文缺失");
    }

    @Test
    void should_return_empty_when_header_missing_on_optional() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(resolver.optional(request)).isEmpty();
    }
}
