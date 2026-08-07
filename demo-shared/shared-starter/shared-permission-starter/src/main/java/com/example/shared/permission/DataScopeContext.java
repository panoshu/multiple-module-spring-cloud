package com.example.shared.permission;

import com.example.auth.api.dto.DataScope;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;

/**
 * 行级数据过滤上下文（ThreadLocal 传递 DataScope）.
 *
 * <p>由 {@link DataScopeAspect} 在方法进入时 set、方法退出时 clear。
 * Repository 通过 {@link DataScopeQueryHelper} 读取。
 *
 * @author shared-permission-starter
 */
public final class DataScopeContext {

    private DataScopeContext() {
    }

    private static final ThreadLocal<DataScope> HOLDER = new ThreadLocal<>();

    public static void set(DataScope scope) {
        HOLDER.set(scope);
    }

    public static DataScope get() {
        DataScope scope = HOLDER.get();
        return scope != null ? scope : DataScope.empty();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /**
     * 获取当前 DataScope，未设置时抛异常（强约束场景用）。
     */
    public static DataScope require() {
        DataScope scope = HOLDER.get();
        if (scope == null) {
            throw new BusinessException(PermissionErrorCode.SESSION_CONTEXT_MISSING)
                .withLogDetail("DataScopeContext 未设置，可能未标注 @DataScope 注解");
        }
        return scope;
    }
}
