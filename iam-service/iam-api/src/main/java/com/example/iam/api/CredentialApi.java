package com.example.iam.api;

import com.example.iam.api.command.ChangeCredentialCommand;
import com.example.iam.api.command.CreateCredentialCommand;
import com.example.iam.api.command.RevokeCredentialCommand;
import com.example.iam.api.dto.CredentialDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListCredentialsQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 凭据管理 API
 *
 * <p>提供凭据的创建、修改、撤销以及列表查询接口。
 *
 * @author iam-service
 */
@HttpExchange("/iam/credentials")
public interface CredentialApi {

    /**
     * 创建凭据
     *
     * @param command 创建命令
     * @return 凭据 ID
     */
    @PostExchange("/create")
    ApiResult<IdResponseDTO> create(@RequestBody @Valid CreateCredentialCommand command);

    /**
     * 修改凭据
     *
     * @param command 修改命令
     * @return 操作结果
     */
    @PostExchange("/change")
    ApiResult<Void> change(@RequestBody @Valid ChangeCredentialCommand command);

    /**
     * 撤销凭据
     *
     * @param command 撤销命令
     * @return 操作结果
     */
    @PostExchange("/revoke")
    ApiResult<Void> revoke(@RequestBody @Valid RevokeCredentialCommand command);

    /**
     * 凭据列表查询
     *
     * @param query 查询条件
     * @return 凭据分页列表
     */
    @PostExchange("/list")
    ApiResult<PageData<CredentialDTO>> list(@RequestBody @Valid ListCredentialsQuery query);
}
