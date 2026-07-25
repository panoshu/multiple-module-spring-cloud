package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import org.mindrot.jbcrypt.BCrypt;

/**
 * 密码凭据验证器（默认实现，使用 BCrypt）
 *
 * <p>BCrypt 自带盐，存储的 secret 即 BCrypt hash，salt 字段未使用</p>
 */
public class PasswordCredentialValidator implements CredentialValidator {

    @Override
    public boolean verify(String input, String storedSecret, String salt, CredentialType credentialType) {
        if (input == null || input.isBlank() || storedSecret == null || storedSecret.isBlank()) {
            return false;
        }
        if (credentialType != CredentialType.PASSWORD) {
            return false;
        }
        try {
            return BCrypt.checkpw(input, storedSecret);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CredentialType supportedType() {
        return CredentialType.PASSWORD;
    }

    /**
     * 对明文密码进行 BCrypt 哈希
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) {
            throw new IllegalArgumentException("plainPassword cannot be blank");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }
}
