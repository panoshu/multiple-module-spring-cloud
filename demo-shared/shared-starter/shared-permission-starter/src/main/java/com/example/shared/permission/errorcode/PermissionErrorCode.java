package com.example.shared.permission.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * shared-permission-starter 模块错误码定义。
 *
 * <p>错误码区间 {@code SHARED.PERM.0001-SHARED.PERM.0099}，遵循 {@code 08-错误码规范.md}。
 *
 * @author shared-permission-starter
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum PermissionErrorCode implements ErrorDefinition {

    /** 权限不足，拒绝访问 */
    PERMISSION_DENIED("SHARED.PERM.0001", "无权限访问"),

    /** 权限校验服务不可达，fail-closed 拒绝 */
    PERMISSION_SERVICE_UNAVAILABLE("SHARED.PERM.0002", "权限校验服务暂不可用"),

    /** 会话上下文签名验证失败 */
    SESSION_SIGNATURE_INVALID("SHARED.PERM.0003", "会话签名验证失败"),

    /** 会话上下文缺失，无法解析操作者 */
    SESSION_CONTEXT_MISSING("SHARED.PERM.0004", "会话上下文缺失"),

    /** 数据范围解析失败 */
    DATA_SCOPE_RESOLVE_FAILED("SHARED.PERM.0005", "数据范围解析失败");

    private final String code;
    private final String message;
}
