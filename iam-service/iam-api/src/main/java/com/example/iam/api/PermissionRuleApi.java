package com.example.iam.api;

import com.example.iam.api.command.CreatePermissionRuleCommand;
import com.example.iam.api.command.DisablePermissionRuleCommand;
import com.example.iam.api.command.EnablePermissionRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PermissionRuleDTO;
import com.example.iam.api.query.GetPermissionRuleDetailQuery;
import com.example.iam.api.query.ListPermissionRulesQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 权限规则管理 API
 *
 * <p>提供权限规则的创建、禁用、启用以及列表/详情查询接口。
 *
 * @author iam-service
 */
@HttpExchange("/iam/permission-rules")
public interface PermissionRuleApi {

    /**
     * 创建权限规则
     *
     * @param command 创建命令
     * @return 权限规则 ID
     */
    @PostExchange("/create")
    ApiResult<IdResponseDTO> create(@RequestBody @Valid CreatePermissionRuleCommand command);

    /**
     * 禁用权限规则
     *
     * @param command 禁用命令
     * @return 操作结果
     */
    @PostExchange("/disable")
    ApiResult<Void> disable(@RequestBody @Valid DisablePermissionRuleCommand command);

    /**
     * 启用权限规则
     *
     * @param command 启用命令
     * @return 操作结果
     */
    @PostExchange("/enable")
    ApiResult<Void> enable(@RequestBody @Valid EnablePermissionRuleCommand command);

    /**
     * 权限规则列表查询
     *
     * @param query 查询条件
     * @return 权限规则分页列表
     */
    @PostExchange("/list")
    ApiResult<PageData<PermissionRuleDTO>> list(@RequestBody @Valid ListPermissionRulesQuery query);

    /**
     * 权限规则详情查询
     *
     * @param query 查询条件
     * @return 权限规则详情
     */
    @PostExchange("/detail")
    ApiResult<PermissionRuleDTO> getDetail(@RequestBody @Valid GetPermissionRuleDetailQuery query);
}
