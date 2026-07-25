package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;

import java.util.List;

/**
 * 凭据二次授权策略（默认实现）
 *
 * <p>从目标用户（internetUserId 对应的账号）的凭据列表中筛选 PASSWORD 类型且 ACTIVE 状态的凭据，
 * 调用 CredentialValidator 验证。任一凭据验证通过即返回 true。</p>
 */
public class CredentialSecondaryAuthStrategy implements SecondaryAuthStrategy {

    @Override
    public boolean authenticate(SecondaryAuthSession session, String input,
                                List<Credential> credentials, CredentialValidator validator) {
        if (input == null || input.isBlank() || credentials == null || credentials.isEmpty()) {
            return false;
        }
        return credentials.stream()
            .filter(c -> c.credentialType() == CredentialType.PASSWORD)
            .filter(c -> c.status() == UserStatus.ACTIVE)
            .anyMatch(c -> c.verify(input, validator));
    }

    @Override
    public SecondaryAuthStrategyType supportedType() {
        return SecondaryAuthStrategyType.CREDENTIAL;
    }
}
