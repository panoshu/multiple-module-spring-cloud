package com.example.iam.api;

import com.example.iam.api.dto.LoginLogDTO;
import com.example.iam.api.query.ListLoginLogsQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 登录日志查询 API
 *
 * <p>提供登录日志的分页查询接口,用于审计与排查。
 *
 * @author iam-service
 */
@HttpExchange("/iam/login-logs")
public interface LoginLogApi {

    /**
     * 登录日志列表查询
     *
     * @param query 查询条件
     * @return 登录日志分页列表
     */
    @PostExchange("/list")
    ApiResult<PageData<LoginLogDTO>> list(@RequestBody @Valid ListLoginLogsQuery query);
}
