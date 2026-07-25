package com.example.iam.domain.authentication.service;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.domain.authentication.strategy.SecondaryAuthStrategy;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.annotation.DomainService;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 二次授权领域服务
 *
 * <p>编排二次授权会话的发起、完成、撤销流程：
 * <ol>
 *   <li>{@link #initiate}：应用层生成会话 ID 后传入，创建 PENDING 状态会话并保存</li>
 *   <li>{@link #complete}：校验会话有效性，调用 {@link SecondaryAuthStrategy} 验证凭据，
 *       验证通过时调用 {@code SecondaryAuthSession.complete()} 并保存</li>
 *   <li>{@link #revoke}：撤销指定会话，会话不存在抛 {@link IllegalStateException}</li>
 * </ol>
 *
 * <p><b>ID 生成策略</b>：本服务不依赖 IdService（保持 domain 层纯粹），
 * 由应用层调用 IdService 生成 {@link SecondaryAuthSessionId} 后传入。</p>
 *
 * <p><b>失败语义</b>：{@code complete} 返回 {@code false} 表示凭据验证未通过或会话不可用，
 * 应用层据此抛出 {@code BusinessException}；{@code revoke} 是管理操作，
 * 会话不存在时直接抛 {@code IllegalStateException} 由应用层转换。</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
@DomainService
public class SecondaryAuthService {

    private static final String INTERNET_USER_OWNER_TYPE = "INTERNET_USER";

    private final SecondaryAuthSessionRepository sessionRepository;
    private final SecondaryAuthStrategy strategy;
    private final CredentialValidator credentialValidator;
    private final CredentialRepository credentialRepository;

    public SecondaryAuthService(SecondaryAuthSessionRepository sessionRepository,
                                SecondaryAuthStrategy strategy,
                                CredentialValidator credentialValidator,
                                CredentialRepository credentialRepository) {
        this.sessionRepository = sessionRepository;
        this.strategy = strategy;
        this.credentialValidator = credentialValidator;
        this.credentialRepository = credentialRepository;
    }

    /**
     * 发起二次授权会话
     *
     * @param sessionId      会话 ID（由应用层生成）
     * @param branchUserId   网点柜员 ID
     * @param internetUserId 经办人 ID
     * @param strategyType   二次授权策略类型
     * @param expiresAt      过期时间
     * @param initiator      发起人
     * @return PENDING 状态的二次授权会话
     */
    public SecondaryAuthSession initiate(SecondaryAuthSessionId sessionId,
                                         BranchUserId branchUserId, InternetUserId internetUserId,
                                         SecondaryAuthStrategyType strategyType,
                                         LocalDateTime expiresAt, UserNo initiator) {
        SecondaryAuthSession session = SecondaryAuthSession.initiate(
            sessionId, branchUserId, internetUserId, strategyType, expiresAt, initiator
        );
        sessionRepository.save(session);
        return session;
    }

    /**
     * 完成二次授权
     *
     * <p>验证流程：
     * <ol>
     *   <li>加载会话，不存在返回 {@code false}</li>
     *   <li>校验会话状态必须为 PENDING，且未过期</li>
     *   <li>查询目标经办人的所有凭据</li>
     *   <li>调用 {@link SecondaryAuthStrategy#authenticate} 验证</li>
     *   <li>验证通过时调用 {@link SecondaryAuthSession#complete} 并保存</li>
     * </ol>
     *
     * @param sessionId 会话 ID
     * @param input     用户输入（如密码）
     * @param operator  操作人
     * @return true 表示验证通过且会话已标记 COMPLETED；false 表示会话不可用或凭据不匹配
     */
    public boolean complete(SecondaryAuthSessionId sessionId, String input, UserNo operator) {
        Optional<SecondaryAuthSession> sessionOpt = sessionRepository.load(sessionId);
        if (sessionOpt.isEmpty()) {
            return false;
        }
        SecondaryAuthSession session = sessionOpt.get();

        if (session.status() != SecondaryAuthStatus.PENDING) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (session.isExpired(now)) {
            return false;
        }

        List<Credential> credentials = credentialRepository.findByOwner(
            INTERNET_USER_OWNER_TYPE, session.internetUserId().value()
        );
        boolean authenticated = strategy.authenticate(session, input, credentials, credentialValidator);
        if (!authenticated) {
            return false;
        }

        session.complete(operator, now);
        sessionRepository.save(session);
        return true;
    }

    /**
     * 撤销二次授权会话
     *
     * @param sessionId 会话 ID
     * @param operator  操作人
     * @throws IllegalStateException 会话不存在时抛出
     */
    public void revoke(SecondaryAuthSessionId sessionId, UserNo operator) {
        SecondaryAuthSession session = sessionRepository.load(sessionId)
            .orElseThrow(() -> new IllegalStateException("二次授权会话不存在: " + sessionId.value()));
        session.revoke(operator);
        sessionRepository.save(session);
    }
}
