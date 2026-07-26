package com.example.core.adapter.context;

import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Base64;
import java.util.Optional;

/**
 * 会话上下文解析器
 *
 * <p>从 HTTP 请求的 {@code X-Session-Context} header 解析 {@link SessionContext}。
 * header 内容为 Base64 编码的 JSON,由 gateway 从 sa-token Token-Session 读取后写入。
 *
 * <p>kernel 不直接依赖 sa-token,通过本组件与 sa-token 解耦,保持可独立测试。
 *
 * <p>通过 {@link RequestContextHolder} 获取当前请求,避免在 Controller 方法签名中
 * 暴露 {@link HttpServletRequest},保持 API 接口契约纯净。
 *
 * @author panoshu
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionContextResolver {

    private static final String SESSION_HEADER = "X-Session-Context";

    private final ObjectMapper objectMapper;

    /**
     * 解析会话上下文,header 缺失时返回 empty。
     */
    public Optional<SessionContext> optional() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(header);
            SessionContext session = objectMapper.readValue(decoded, SessionContext.class);
            return Optional.of(session);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("解析 X-Session-Context header 失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 解析会话上下文,header 缺失时抛 BusinessException。
     */
    public SessionContext require() {
        return optional()
            .orElseThrow(() -> new BusinessException(CommonError.UNAUTHORIZED)
                .withUserDetail("会话上下文缺失,请重新登录")
                .withLogDetail("X-Session-Context header 缺失或解析失败"));
    }

    /**
     * 测试专用:从指定请求解析会话上下文。
     */
    public Optional<SessionContext> optional(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        String header = request.getHeader(SESSION_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(header);
            return Optional.of(objectMapper.readValue(decoded, SessionContext.class));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("解析 X-Session-Context header 失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 测试专用:从指定请求解析,缺失时抛异常。
     */
    public SessionContext require(HttpServletRequest request) {
        return optional(request)
            .orElseThrow(() -> new BusinessException(CommonError.UNAUTHORIZED)
                .withUserDetail("会话上下文缺失,请重新登录")
                .withLogDetail("X-Session-Context header 缺失或解析失败"));
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return servletAttrs.getRequest();
        }
        return null;
    }
}
