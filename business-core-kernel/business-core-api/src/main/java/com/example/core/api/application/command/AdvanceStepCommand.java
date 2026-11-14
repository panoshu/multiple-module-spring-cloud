package com.example.core.api.application.command;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 推进申请单命令（spec §15.1 推进类 Command）。
 * <p>
 * 推进类请求必须携带期望步骤与请求编号：{@code expectedStep} 防止过期请求推进到错误步骤，
 * {@code requestNo} 作为跨服务幂等键，同一请求重复提交只执行一次。{@code operator} 为空时
 * 由编排器从申请单上下文派生；{@code actionPayload} 为可选业务参数，当前版本暂不消费。
 *
 * @author panoshu
 */
public record AdvanceStepCommand(
  @NotBlank(message = "申请单ID不能为空") String applicationId,
  @NotBlank(message = "期望步骤不能为空") String expectedStep,
  @NotBlank(message = "请求编号不能为空") String requestNo,
  String operator,
  Map<String, Object> actionPayload
) {
}
