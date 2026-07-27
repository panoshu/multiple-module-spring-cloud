package com.example.iam.api;

import com.example.iam.api.dto.PermissionSnapshotDTO;
import com.example.iam.api.query.ResolvePermissionsQuery;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 权限解析 API
 *
 * <p>供调试与预览使用的权限解析接口,返回当前用户在指定上下文下的权限快照。
 *
 * @author iam-service
 */
@HttpExchange("/iam/permission-resolver")
public interface PermissionResolverApi {

    /**
     * 解析权限
     *
     * @param query 查询条件
     * @return 权限快照
     */
    @PostExchange("/resolve")
    ApiResult<PermissionSnapshotDTO> resolve(@RequestBody @Valid ResolvePermissionsQuery query);
}
