package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;

import java.util.List;

/**
 * 二次授权策略接口（开闭原则扩展点）
 *
 * <p>每种二次授权方式对应一个实现：
 * <ul>
 *   <li>CREDENTIAL → CredentialSecondaryAuthStrategy（凭据验证，默认实现）</li>
 *   <li>AUTHORIZATION_CODE → AuthorizationCodeSecondaryAuthStrategy（未来，授权码）</li>
 *   <li>SCAN → ScanSecondaryAuthStrategy（未来，扫码）</li>
 * </ul>
 */
public interface SecondaryAuthStrategy {

    /**
     * 执行二次授权验证
     *
     * @param session       二次授权会话
     * @param input         用户输入（如密码、授权码）
     * @param credentials   目标用户的所有凭据
     * @param validator     凭据验证器
     * @return true 如果验证通过
     */
    boolean authenticate(SecondaryAuthSession session, String input,
                         List<Credential> credentials, CredentialValidator validator);

    /**
     * 该策略支持的二次授权类型
     */
    SecondaryAuthStrategyType supportedType();
}
