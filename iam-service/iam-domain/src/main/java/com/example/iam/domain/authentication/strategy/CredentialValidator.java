package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;

/**
 * 凭据验证策略接口（开闭原则扩展点）
 *
 * <p>每种凭据类型对应一个实现：
 * <ul>
 *   <li>PASSWORD → PasswordCredentialValidator（BCrypt）</li>
 *   <li>UKEY → UKeyCredentialValidator（未来）</li>
 *   <li>OTP → OTPCredentialValidator（未来）</li>
 * </ul>
 */
public interface CredentialValidator {

    /**
     * 验证用户输入的凭据是否匹配存储的凭据
     *
     * @param input          用户输入的凭据（如明文密码）
     * @param storedSecret   存储的凭据密文（如 BCrypt hash）
     * @param salt           盐值（部分算法可能不用）
     * @param credentialType 凭据类型
     * @return true 如果验证通过
     */
    boolean verify(String input, String storedSecret, String salt, CredentialType credentialType);

    /**
     * 该策略支持的凭据类型
     */
    CredentialType supportedType();
}
