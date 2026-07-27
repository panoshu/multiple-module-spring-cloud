package com.example.iam.api;

import com.example.iam.api.command.ConfirmSecondaryAuthCommand;
import com.example.iam.api.command.InternetLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.command.RejectSecondaryAuthCommand;
import com.example.iam.api.command.RevokeSecondaryAuthCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 网上渠道认证 API
 *
 * <p>面向互联网用户的登录、登出及二次授权确认/拒绝/撤销接口。
 *
 * @author iam-service
 */
@HttpExchange("/internet/auth")
public interface InternetAuthApi {

    /**
     * 网上渠道登录
     *
     * @param command 登录命令
     * @return 登录结果
     */
    @PostExchange("/login")
    ApiResult<LoginResultDTO> login(@RequestBody @Valid InternetLoginCommand command);

    /**
     * 登出
     *
     * @param command 登出命令
     * @return 操作结果
     */
    @PostExchange("/logout")
    ApiResult<Void> logout(@RequestBody @Valid LogoutCommand command);

    /**
     * 确认二次授权
     *
     * @param command 确认命令
     * @return 二次授权会话
     */
    @PostExchange("/secondary-auth/confirm")
    ApiResult<SecondaryAuthSessionDTO> confirmSecondaryAuth(@RequestBody @Valid ConfirmSecondaryAuthCommand command);

    /**
     * 拒绝二次授权
     *
     * @param command 拒绝命令
     * @return 操作结果
     */
    @PostExchange("/secondary-auth/reject")
    ApiResult<Void> rejectSecondaryAuth(@RequestBody @Valid RejectSecondaryAuthCommand command);

    /**
     * 撤销二次授权
     *
     * @param command 撤销命令
     * @return 操作结果
     */
    @PostExchange("/secondary-auth/revoke")
    ApiResult<Void> revokeSecondaryAuth(@RequestBody @Valid RevokeSecondaryAuthCommand command);
}
