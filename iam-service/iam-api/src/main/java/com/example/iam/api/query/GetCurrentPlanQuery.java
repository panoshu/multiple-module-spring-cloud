package com.example.iam.api.query;

/**
 * 查询当前已选计划
 *
 * <p>查询当前登录用户已选定的业务计划。
 * 服务端从 sa-token Token-Session 读取当前已选计划信息,无需客户端传参。
 *
 * @author iam-service
 */
public record GetCurrentPlanQuery() {
}
