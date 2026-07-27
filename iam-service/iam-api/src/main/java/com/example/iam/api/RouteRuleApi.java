package com.example.iam.api;

import com.example.iam.api.command.CreateRouteRuleCommand;
import com.example.iam.api.command.DisableRouteRuleCommand;
import com.example.iam.api.command.EnableRouteRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.RouteRuleDTO;
import com.example.iam.api.query.GetRouteRuleDetailQuery;
import com.example.iam.api.query.ListRouteRulesQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 路由规则管理 API
 *
 * <p>提供路由规则的创建、禁用、启用以及列表/详情查询接口。
 *
 * @author iam-service
 */
@HttpExchange("/iam/route-rules")
public interface RouteRuleApi {

    /**
     * 创建路由规则
     *
     * @param command 创建命令
     * @return 路由规则 ID
     */
    @PostExchange("/create")
    ApiResult<IdResponseDTO> create(@RequestBody @Valid CreateRouteRuleCommand command);

    /**
     * 禁用路由规则
     *
     * @param command 禁用命令
     * @return 操作结果
     */
    @PostExchange("/disable")
    ApiResult<Void> disable(@RequestBody @Valid DisableRouteRuleCommand command);

    /**
     * 启用路由规则
     *
     * @param command 启用命令
     * @return 操作结果
     */
    @PostExchange("/enable")
    ApiResult<Void> enable(@RequestBody @Valid EnableRouteRuleCommand command);

    /**
     * 路由规则列表查询
     *
     * @param query 查询条件
     * @return 路由规则分页列表
     */
    @PostExchange("/list")
    ApiResult<PageData<RouteRuleDTO>> list(@RequestBody @Valid ListRouteRulesQuery query);

    /**
     * 路由规则详情查询
     *
     * @param query 查询条件
     * @return 路由规则详情
     */
    @PostExchange("/detail")
    ApiResult<RouteRuleDTO> getDetail(@RequestBody @Valid GetRouteRuleDetailQuery query);
}
