package com.example.iam.api;

import com.example.iam.api.command.CreateBusinessDefinitionCommand;
import com.example.iam.api.command.DisableBusinessDefinitionCommand;
import com.example.iam.api.command.EnableBusinessDefinitionCommand;
import com.example.iam.api.dto.BusinessDefinitionDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListBusinessDefinitionsQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 业务定义管理 API
 *
 * <p>提供业务定义的创建、禁用、启用以及列表查询接口。
 *
 * @author iam-service
 */
@HttpExchange("/iam/business-definitions")
public interface BusinessDefinitionApi {

    /**
     * 创建业务定义
     *
     * @param command 创建命令
     * @return 业务定义 ID
     */
    @PostExchange("/create")
    ApiResult<IdResponseDTO> create(@RequestBody @Valid CreateBusinessDefinitionCommand command);

    /**
     * 禁用业务定义
     *
     * @param command 禁用命令
     * @return 操作结果
     */
    @PostExchange("/disable")
    ApiResult<Void> disable(@RequestBody @Valid DisableBusinessDefinitionCommand command);

    /**
     * 启用业务定义
     *
     * @param command 启用命令
     * @return 操作结果
     */
    @PostExchange("/enable")
    ApiResult<Void> enable(@RequestBody @Valid EnableBusinessDefinitionCommand command);

    /**
     * 业务定义列表查询
     *
     * @param query 查询条件
     * @return 业务定义分页列表
     */
    @PostExchange("/list")
    ApiResult<PageData<BusinessDefinitionDTO>> list(@RequestBody @Valid ListBusinessDefinitionsQuery query);
}
