package com.example.approval.domain.gateway;

import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.UserNo;

import java.util.List;

/**
 * 角色用户网关接口（防腐层）
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public interface RoleUserGateway {

    /**
     * 获取角色对应的用户列表
     *
     * TODO: 待用户服务API提供后实现
     *
     * @param roleId 角色ID
     * @return 用户列表
     */
    List<UserNo> getUsersByRole(String roleId);

    /**
     * 获取同一计划下的用户列表（排除指定用户）
     *
     * TODO: 待用户服务API提供后实现
     *
     * @param planId      计划ID
     * @param excludeUser 要排除的用户
     * @return 用户列表
     */
    List<UserNo> getUsersInSamePlan(PlanNo planId, UserNo excludeUser);
}