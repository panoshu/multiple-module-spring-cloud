package com.example.approval.infrastructure.gateway;

import com.example.approval.domain.gateway.RoleUserGateway;
import com.example.shared.primitives.identity.UserNo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 角色用户网关实现（伪实现）
 *
 * TODO: 待用户服务API提供后实现
 *
 * @author approval-service
 */
@Component
@RequiredArgsConstructor
public class RoleUserGatewayImpl implements RoleUserGateway {

    @Override
    public List<UserNo> getUsersByRole(String roleId) {
        // TODO: 待用户服务API提供后实现
        // 实际实现应该调用用户服务API获取角色下的用户列表
        return List.of();
    }
}