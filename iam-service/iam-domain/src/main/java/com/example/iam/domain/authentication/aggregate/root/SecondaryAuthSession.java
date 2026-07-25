package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 二次授权会话聚合根
 *
 * <p>网点柜员（BranchUser）通过二次授权获得经办人（InternetUser）的业务办理权限。
 * 会话有 PENDING / COMPLETED / REVOKED / EXPIRED 四种状态。
 * 会话过期时间默认 30 分钟。</p>
 */
public class SecondaryAuthSession extends AggregateRoot<SecondaryAuthSessionId> {

    private final BranchUserId branchUserId;
    private final InternetUserId internetUserId;
    private final SecondaryAuthStrategyType strategyType;
    private final LocalDateTime expiresAt;
    private SecondaryAuthStatus status;
    private LocalDateTime completedAt;

    private SecondaryAuthSession(SecondaryAuthSessionId id,
                                 BranchUserId branchUserId, InternetUserId internetUserId,
                                 SecondaryAuthStrategyType strategyType,
                                 LocalDateTime expiresAt,
                                 SecondaryAuthStatus status, LocalDateTime completedAt,
                                 UserNo createdBy, UserNo updatedBy,
                                 LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.branchUserId = branchUserId;
        this.internetUserId = internetUserId;
        this.strategyType = strategyType;
        this.expiresAt = expiresAt;
        this.status = status;
        this.completedAt = completedAt;
        validateInvariants();
    }

    public static SecondaryAuthSession initiate(SecondaryAuthSessionId id,
                                                BranchUserId branchUserId, InternetUserId internetUserId,
                                                SecondaryAuthStrategyType strategyType,
                                                LocalDateTime expiresAt, UserNo initiator) {
        return new SecondaryAuthSession(id, branchUserId, internetUserId, strategyType, expiresAt,
            SecondaryAuthStatus.PENDING, null,
            initiator, initiator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static SecondaryAuthSession reconstitute(SecondaryAuthSessionId id,
                                                    BranchUserId branchUserId, InternetUserId internetUserId,
                                                    SecondaryAuthStrategyType strategyType,
                                                    LocalDateTime expiresAt,
                                                    SecondaryAuthStatus status, LocalDateTime completedAt,
                                                    UserNo createdBy, UserNo updatedBy,
                                                    LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new SecondaryAuthSession(id, branchUserId, internetUserId, strategyType, expiresAt,
            status, completedAt, createdBy, updatedBy, createdAt, updatedAt, version);
    }

    /**
     * 完成二次授权
     *
     * @throws IllegalStateException 如果会话已完成、已撤销或已过期
     */
    public void complete(UserNo operator, LocalDateTime now) {
        if (status == SecondaryAuthStatus.COMPLETED) {
            throw new IllegalStateException("二次授权会话已完成");
        }
        if (status == SecondaryAuthStatus.REVOKED) {
            throw new IllegalStateException("二次授权会话已撤销");
        }
        if (isExpired(now)) {
            throw new IllegalStateException("二次授权会话已过期");
        }
        this.status = SecondaryAuthStatus.COMPLETED;
        this.completedAt = now;
        markUpdated(operator);
    }

    /**
     * 撤销二次授权会话
     *
     * @throws IllegalStateException 如果会话已完成
     */
    public void revoke(UserNo operator) {
        if (status == SecondaryAuthStatus.COMPLETED) {
            throw new IllegalStateException("已完成的会话不能撤销");
        }
        if (status == SecondaryAuthStatus.REVOKED) {
            return; // 幂等：已撤销会话直接返回
        }
        this.status = SecondaryAuthStatus.REVOKED;
        markUpdated(operator);
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }

    @Override
    protected void validateInvariants() {
        if (branchUserId == null) throw new IllegalArgumentException("branchUserId cannot be null");
        if (internetUserId == null) throw new IllegalArgumentException("internetUserId cannot be null");
        if (strategyType == null) throw new IllegalArgumentException("strategyType cannot be null");
        if (expiresAt == null) throw new IllegalArgumentException("expiresAt cannot be null");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public BranchUserId branchUserId() { return branchUserId; }
    public InternetUserId internetUserId() { return internetUserId; }
    public SecondaryAuthStrategyType strategyType() { return strategyType; }
    public LocalDateTime expiresAt() { return expiresAt; }
    public SecondaryAuthStatus status() { return status; }
    public LocalDateTime completedAt() { return completedAt; }
}
