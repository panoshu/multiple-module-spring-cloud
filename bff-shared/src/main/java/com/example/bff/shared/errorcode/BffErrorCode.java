package com.example.bff.shared.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * BFF 层错误码
 *
 * @author bff
 */
public enum BffErrorCode implements ErrorDefinition {

    ROUTE_NOT_FOUND("SERVICE.BFF.0001", "未找到业务类型路由"),
    DOWNSTREAM_SERVICE_ERROR("SERVICE.BFF.0002", "下游服务调用失败"),
    AGGREGATION_ERROR("SERVICE.BFF.0003", "数据聚合失败"),
    KERNEL_API_NOT_FOUND("SERVICE.BFF.0004", "未找到服务的 kernel API 代理");

    private final String code;
    private final String message;

    BffErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getMessage() {
        return this.message;
    }
}
