package com.example.iam.api;

import com.example.iam.api.command.BranchLoginCommand;
import com.example.iam.api.command.InitiateSecondaryAuthCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.iam.api.query.GetSecondaryAuthStatusQuery;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 网点渠道认证 API
 *
 * <p>面向网点柜员的登录、登出、二次授权发起与状态查询接口。
 *
 * @author iam-service
 */
@HttpExchange("/branch/auth")
public interface BranchAuthApi {

    /**
     * 网点渠道登录
     *
     * @param command 登录命令
     * @return 登录结果
     */
    @PostExchange("/login")
    ApiResult<LoginResultDTO> login(@RequestBody @Valid BranchLoginCommand command);

    /**
     * 登出
     *
     * @param command 登出命令
     * @return 操作结果
     */
    @PostExchange("/logout")
    ApiResult<Void> logout(@RequestBody @Valid LogoutCommand command);

    /**
     * 发起二次授权
     *
     * @param command 发起命令
     * @return 二次授权会话
     */
    @PostExchange("/secondary-auth/initiate")
    ApiResult<SecondaryAuthSessionDTO> initiateSecondaryAuth(@RequestBody @Valid InitiateSecondaryAuthCommand command);

    /**
     * 查询二次授权状态
     *
     * @param query 状态查询
     * @return 二次授权会话
     */
    @PostExchange("/secondary-auth/status")
    ApiResult<SecondaryAuthSessionDTO> getSecondaryAuthStatus(@RequestBody @Valid GetSecondaryAuthStatusQuery query);
}
