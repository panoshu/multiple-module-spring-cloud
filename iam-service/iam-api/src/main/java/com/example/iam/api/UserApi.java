package com.example.iam.api;

import com.example.iam.api.command.CreateUserCommand;
import com.example.iam.api.command.DisableUserCommand;
import com.example.iam.api.command.EnableUserCommand;
import com.example.iam.api.command.LockUserCommand;
import com.example.iam.api.command.UpdateUserProfileCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.UserDTO;
import com.example.iam.api.query.GetUserDetailQuery;
import com.example.iam.api.query.ListUsersQuery;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 用户管理 API
 *
 * <p>提供用户的创建、禁用、启用、锁定、资料更新以及列表/详情查询接口。
 *
 * @author iam-service
 */
@HttpExchange("/iam/users")
public interface UserApi {

    /**
     * 创建用户
     *
     * @param command 创建命令
     * @return 用户 ID
     */
    @PostExchange("/create")
    ApiResult<IdResponseDTO> create(@RequestBody @Valid CreateUserCommand command);

    /**
     * 禁用用户
     *
     * @param command 禁用命令
     * @return 操作结果
     */
    @PostExchange("/disable")
    ApiResult<Void> disable(@RequestBody @Valid DisableUserCommand command);

    /**
     * 启用用户
     *
     * @param command 启用命令
     * @return 操作结果
     */
    @PostExchange("/enable")
    ApiResult<Void> enable(@RequestBody @Valid EnableUserCommand command);

    /**
     * 锁定用户
     *
     * @param command 锁定命令
     * @return 操作结果
     */
    @PostExchange("/lock")
    ApiResult<Void> lock(@RequestBody @Valid LockUserCommand command);

    /**
     * 更新用户资料
     *
     * @param command 更新命令
     * @return 操作结果
     */
    @PostExchange("/profile/update")
    ApiResult<Void> updateProfile(@RequestBody @Valid UpdateUserProfileCommand command);

    /**
     * 用户列表查询
     *
     * @param query 查询条件
     * @return 用户分页列表
     */
    @PostExchange("/list")
    ApiResult<PageData<UserDTO>> list(@RequestBody @Valid ListUsersQuery query);

    /**
     * 用户详情查询
     *
     * @param query 查询条件
     * @return 用户详情
     */
    @PostExchange("/detail")
    ApiResult<UserDTO> getDetail(@RequestBody @Valid GetUserDetailQuery query);
}
