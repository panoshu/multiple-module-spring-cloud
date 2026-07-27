package com.example.iam.api;

import com.example.iam.api.command.HqLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 总部渠道认证 API
 *
 * <p>面向总部后台用户的登录与登出接口。
 *
 * @author iam-service
 */
@HttpExchange("/hq/auth")
public interface HqAuthApi {

    /**
     * 总部渠道登录
     *
     * @param command 登录命令
     * @return 登录结果
     */
    @PostExchange("/login")
    ApiResult<LoginResultDTO> login(@RequestBody @Valid HqLoginCommand command);

    /**
     * 登出
     *
     * @param command 登出命令
     * @return 操作结果
     */
    @PostExchange("/logout")
    ApiResult<Void> logout(@RequestBody @Valid LogoutCommand command);
}
