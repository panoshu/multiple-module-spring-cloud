package com.example.iam.domain.authentication.service;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.InternetUserId;
import com.example.shared.domain.annotation.DomainService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Optional;

/**
 * 身份切换领域服务
 *
 * <p>负责校验网点柜员当前是否存在有效的二次授权会话（即是否已切换为经办人身份）。
 * 实际的身份切换操作（sa-token {@code switchTo}）由应用层调用 {@code StpBranchUtil} 完成，
 * domain 层仅提供查询与判断能力。</p>
 *
 * <p><b>有效代理会话定义</b>：状态为 {@link SecondaryAuthStatus#COMPLETED} 且未过期。
 * PENDING/REVOKED 状态的会话不构成代理身份。</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
@DomainService
public class IdentitySwitchService {

    private final SecondaryAuthSessionRepository sessionRepository;

    public IdentitySwitchService(SecondaryAuthSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 查询柜员当前代理的经办人 ID
     *
     * <p>从柜员的所有有效会话中筛选 COMPLETED 且未过期的会话，
     * 若存在多个则返回最近完成的会话对应的经办人 ID</p>
     *
     * @param branchUserId 网点柜员 ID
     * @return 经办人 ID，若无有效代理会话返回 empty
     */
    public Optional<InternetUserId> getCurrentActingAs(BranchUserId branchUserId) {
        LocalDateTime now = LocalDateTime.now();
        return sessionRepository.findActiveByBranchUser(branchUserId).stream()
            .filter(s -> s.status() == SecondaryAuthStatus.COMPLETED)
            .filter(s -> !s.isExpired(now))
            .max(Comparator.comparing(
                SecondaryAuthSession::completedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())
            ))
            .map(SecondaryAuthSession::internetUserId);
    }

    /**
     * 判断柜员是否可以切换回柜员身份
     *
     * <p>当且仅当柜员当前存在有效的代理身份时可切换回</p>
     *
     * @param branchUserId 网点柜员 ID
     * @return true 表示当前存在代理身份，可切换回
     */
    public boolean canSwitchBack(BranchUserId branchUserId) {
        return getCurrentActingAs(branchUserId).isPresent();
    }
}
