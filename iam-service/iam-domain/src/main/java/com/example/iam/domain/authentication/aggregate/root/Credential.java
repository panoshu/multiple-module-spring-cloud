package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 凭据聚合根
 *
 * <p>一个账号可有多条凭据（不同类型），类型通过 CredentialType 枚举扩展，
 * 验证逻辑通过 CredentialValidator 策略接口扩展（开闭原则）</p>
 */
public class Credential extends AggregateRoot<CredentialId> {

    private final String ownerType;     // INTERNET_USER / HQ_USER / BRANCH_USER
    private final Long ownerId;
    private final CredentialType credentialType;
    private String secret;
    private String salt;
    private UserStatus status;
    private LocalDateTime lastChangedAt;

    private Credential(CredentialId id, String ownerType, Long ownerId, CredentialType credentialType,
                      String secret, String salt, UserStatus status, LocalDateTime lastChangedAt,
                      UserNo createdBy, UserNo updatedBy,
                      LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        super(id, createdBy, updatedBy, createdAt, updatedAt, version);
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.credentialType = credentialType;
        this.secret = secret;
        this.salt = salt;
        this.status = status;
        this.lastChangedAt = lastChangedAt;
        validateInvariants();
    }

    public static Credential create(CredentialId id, CredentialType credentialType,
                                    String secret, String salt, UserNo creator) {
        return new Credential(id, null, null, credentialType, secret, salt,
            UserStatus.ACTIVE, LocalDateTime.now(),
            creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static Credential createWithOwner(CredentialId id, String ownerType, Long ownerId,
                                             CredentialType credentialType,
                                             String secret, String salt, UserNo creator) {
        return new Credential(id, ownerType, ownerId, credentialType, secret, salt,
            UserStatus.ACTIVE, LocalDateTime.now(),
            creator, creator, LocalDateTime.now(), LocalDateTime.now(), Version.initial());
    }

    public static Credential reconstitute(CredentialId id, String ownerType, Long ownerId,
                                          CredentialType credentialType,
                                          String secret, String salt, UserStatus status,
                                          LocalDateTime lastChangedAt,
                                          UserNo createdBy, UserNo updatedBy,
                                          LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
        return new Credential(id, ownerType, ownerId, credentialType, secret, salt, status, lastChangedAt,
            createdBy, updatedBy, createdAt, updatedAt, version);
    }

    /**
     * 验证输入的凭据是否匹配
     */
    public boolean verify(String input, CredentialValidator validator) {
        if (status != UserStatus.ACTIVE) {
            return false;
        }
        return validator.verify(input, this.secret, this.salt, this.credentialType);
    }

    public void changeSecret(String newSecret, String newSalt, UserNo operator) {
        if (newSecret == null || newSecret.isBlank()) {
            throw new IllegalArgumentException("newSecret cannot be blank");
        }
        this.secret = newSecret;
        this.salt = newSalt;
        this.lastChangedAt = LocalDateTime.now();
        markUpdated(operator);
    }

    public void disable(UserNo operator) {
        this.status = UserStatus.DISABLED;
        markUpdated(operator);
    }

    @Override
    protected void validateInvariants() {
        if (credentialType == null) throw new IllegalArgumentException("credentialType cannot be null");
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("secret cannot be blank");
        if (status == null) throw new IllegalArgumentException("status cannot be null");
    }

    public String ownerType() { return ownerType; }
    public Long ownerId() { return ownerId; }
    public CredentialType credentialType() { return credentialType; }
    public String secret() { return secret; }
    public String salt() { return salt; }
    public UserStatus status() { return status; }
    public LocalDateTime lastChangedAt() { return lastChangedAt; }
}
