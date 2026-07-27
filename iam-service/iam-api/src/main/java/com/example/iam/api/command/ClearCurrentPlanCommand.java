package com.example.iam.api.command;

/**
 * 清除当前计划选择命令
 *
 * <p>清除当前会话已选定的办理计划,服务端依据当前登录上下文操作,无需客户端传参。
 *
 * @author iam-service
 */
public record ClearCurrentPlanCommand() {
}
