package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authentication.event.CredentialChangedEvent;
import com.example.iam.domain.authentication.event.CredentialCreatedEvent;
import com.example.iam.domain.authentication.event.CredentialExpiredEvent;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 凭据聚合根。
 *
 * <p>承载用户登录凭据(密码/UKey/动态令牌)的密文、盐值、辅助数据与状态。
 * 一个用户可同时持有多种类型的凭据(每种类型一条记录),通过 {@link CredentialType} 区分。
 *
 * <p>状态机参照 {@link CredentialStatus}:
 * <ul>
 *   <li>{@code create} → ACTIVE</li>
 *   <li>ACTIVE → EXPIRED(自然过期,由 {@link #markExpired()} 显式触发)</li>
 *   <li>ACTIVE → REVOKED(主动撤销)</li>
 *   <li>EXPIRED → REVOKED(过期后撤销)</li>
 * </ul>
 *
 * <p>REVOKED 为终态,不可恢复。EXPIRED 状态下允许 {@link #change} 更新密文,
 * 但不会自动恢复为 ACTIVE(需另行创建新凭据)。
 *
 * <p>验证流程通过 {@link CredentialValidator} SPI 委托给具体策略,
 * 策略实现位于 {@code iam-infrastructure} 层。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public class Credential extends AggregateRoot<CredentialId> {

  private final String ownerType;
  private final Long ownerId;
  private final CredentialType credentialType;
  private String secretHash;
  private String salt;
  private Map<String, String> auxData;
  private CredentialStatus status;
  private LocalDateTime expireTime;

  private Credential(CredentialId id, String ownerType, Long ownerId,
                     CredentialType credentialType,
                     String secretHash, String salt, Map<String, String> auxData,
                     CredentialStatus status, LocalDateTime expireTime,
                     UserNo createdBy, UserNo updatedBy,
                     LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.ownerType = ownerType;
    this.ownerId = ownerId;
    this.credentialType = credentialType;
    this.secretHash = secretHash;
    this.salt = salt;
    this.auxData = copyAux(auxData);
    this.status = status;
    this.expireTime = expireTime;
    this.validateInvariants();
  }

  /**
   * 工厂方法:创建新凭据(初始状态 ACTIVE)。
   *
   * @param id             凭据 ID
   * @param ownerType      归属类型(如 {@code INTERNET_USER}/{@code HQ_USER}/{@code BRANCH_USER})
   * @param ownerId        归属实体 ID(User.id)
   * @param credentialType 凭据类型
   * @param secretHash     密文(BCrypt 哈希/RSA 公钥指纹/TOTP seed 等)
   * @param salt           盐值(可空,BCrypt 内嵌盐时为 null)
   * @param auxData        辅助数据(如 UKey 公钥、动态令牌计数器等)
   * @param expireTime     过期时间(可空,表示永久凭据)
   * @param createdBy      创建人
   * @return 新建的凭据聚合根
   */
  public static Credential create(CredentialId id, String ownerType, Long ownerId,
                                   CredentialType credentialType,
                                   String secretHash, String salt, Map<String, String> auxData,
                                   LocalDateTime expireTime, UserNo createdBy) {
    Objects.requireNonNull(ownerId, "ownerId cannot be null");
    Objects.requireNonNull(credentialType, "credentialType cannot be null");
    if (ownerType == null || ownerType.isBlank()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_INVALID)
          .withUserDetail("凭据归属类型不能为空");
    }
    if (secretHash == null || secretHash.isBlank()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_INVALID)
          .withUserDetail("凭据密文不能为空");
    }
    LocalDateTime now = LocalDateTime.now();
    Credential credential = new Credential(id, ownerType, ownerId, credentialType,
        secretHash, salt, auxData,
        CredentialStatus.ACTIVE, expireTime,
        createdBy, createdBy, now, now, Version.initial());
    credential.registerDomainEvent(CredentialCreatedEvent.of(
        id, ownerId, ownerType, credentialType));
    return credential;
  }

  /**
   * 工厂方法:从数据库重建聚合。
   */
  public static Credential reconstitute(CredentialId id, String ownerType, Long ownerId,
                                         CredentialType credentialType,
                                         String secretHash, String salt, Map<String, String> auxData,
                                         CredentialStatus status, LocalDateTime expireTime,
                                         UserNo createdBy, UserNo updatedBy,
                                         LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    return new Credential(id, ownerType, ownerId, credentialType,
        secretHash, salt, auxData,
        status, expireTime,
        createdBy, updatedBy, createdAt, updatedAt, version);
  }

  /**
   * 验证凭据。
   *
   * <p>流程:
   * <ol>
   *   <li>校验验证器类型与凭据类型一致(策略匹配)</li>
   *   <li>校验状态非 EXPIRED/REVOKED</li>
   *   <li>委托给 {@link CredentialValidator#validate} 执行具体算法</li>
   * </ol>
   *
   * @param plainSecret 用户提交的明文凭据
   * @param validator   验证策略(由应用层根据 {@link #credentialType()} 选择)
   * @return 验证通过返回 true,失败返回 false
   * @throws DomainException 类型不匹配或状态不允许校验时抛出
   */
  public boolean verify(String plainSecret, CredentialValidator validator) {
    Objects.requireNonNull(validator, "validator cannot be null");
    if (validator.supports() != this.credentialType) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_TYPE_NOT_SUPPORTED)
          .withUserDetail("验证器类型与凭据类型不匹配")
          .withContext("credentialType", this.credentialType)
          .withContext("validatorSupports", validator.supports());
    }
    if (status.isRevoked()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_REVOKED)
          .withUserDetail("凭据已撤销,不可校验")
          .withContext("credentialId", id().value())
          .withContext("status", status);
    }
    if (isExpired()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_EXPIRED)
          .withUserDetail("凭据已过期,不可校验")
          .withContext("credentialId", id().value())
          .withContext("status", status)
          .withContext("expireTime", expireTime);
    }
    return validator.validate(plainSecret, this);
  }

  /**
   * 更新凭据密文与辅助数据。
   *
   * <p>仅 REVOKED 终态禁止更新。EXPIRED 状态下允许更新(用于密钥轮换场景)。
   *
   * <p>注册 {@link CredentialChangedEvent},触发后踢人下线 + 清缓存(由事件监听器执行)。
   *
   * @param newSecretHash 新密文
   * @param newSalt       新盐值(可空)
   * @param newAuxData    新辅助数据(可空,空时清空)
   * @param operator      操作人
   */
  public void change(String newSecretHash, String newSalt, Map<String, String> newAuxData, UserNo operator) {
    if (status.isRevoked()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_REVOKED)
          .withUserDetail("已撤销凭据不可更新")
          .withContext("credentialId", id().value())
          .withContext("status", status);
    }
    if (newSecretHash == null || newSecretHash.isBlank()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_INVALID)
          .withUserDetail("新密文不能为空");
    }
    this.secretHash = newSecretHash;
    this.salt = newSalt;
    this.auxData = copyAux(newAuxData);
    markUpdated(operator);
    registerDomainEvent(CredentialChangedEvent.of(id(), ownerId, credentialType, operator));
  }

  /**
   * 标记凭据为过期(ACTIVE → EXPIRED)。
   *
   * <p>仅 ACTIVE 状态可标记过期。EXPIRED 状态再次标记属于幂等(直接返回,不变更状态)。
   * REVOKED 终态禁止此操作。
   *
   * <p>注册 {@link CredentialExpiredEvent},触发后记录审计日志。
   */
  public void markExpired() {
    if (status.isExpired()) {
      return;
    }
    if (!status.canMarkExpired()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_INVALID)
          .withUserDetail("当前状态不允许标记过期")
          .withContext("credentialId", id().value())
          .withContext("currentStatus", status)
          .withContext("targetStatus", CredentialStatus.EXPIRED);
    }
    this.status = CredentialStatus.EXPIRED;
    registerDomainEvent(CredentialExpiredEvent.of(id(), ownerId, credentialType));
  }

  /**
   * 撤销凭据(ACTIVE/EXPIRED → REVOKED 终态)。
   *
   * <p>REVOKED 状态再次撤销抛出异常(终态不可恢复)。
   *
   * @param operator 操作人
   */
  public void markRevoked(UserNo operator) {
    if (status.isRevoked()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_REVOKED)
          .withUserDetail("凭据已撤销,不可重复撤销")
          .withContext("credentialId", id().value())
          .withContext("status", status);
    }
    if (!status.canRevoke()) {
      throw new DomainException(IamAuthErrorCode.CREDENTIAL_INVALID)
          .withUserDetail("当前状态不允许撤销")
          .withContext("credentialId", id().value())
          .withContext("currentStatus", status)
          .withContext("targetStatus", CredentialStatus.REVOKED);
    }
    this.status = CredentialStatus.REVOKED;
    markUpdated(operator);
  }

  /**
   * 判断凭据是否已过期(自然过期 + 状态过期)。
   *
   * <p>当 {@link #expireTime} 已过时即使状态仍为 ACTIVE 也视为过期。
   *
   * @return 已过期返回 true
   */
  public boolean isExpired() {
    if (status.isExpired()) {
      return true;
    }
    return expireTime != null && expireTime.isBefore(LocalDateTime.now());
  }

  /**
   * 判断凭据是否可校验(ACTIVE 且未自然过期)。
   */
  public boolean isActive() {
    return status.isActive() && !isExpired();
  }

  public String ownerType() { return ownerType; }
  public Long ownerId() { return ownerId; }
  public CredentialType credentialType() { return credentialType; }
  public String secretHash() { return secretHash; }
  public String salt() { return salt; }

  /**
   * 返回辅助数据的不可变视图。
   */
  public Map<String, String> auxData() {
    return Collections.unmodifiableMap(auxData);
  }

  public CredentialStatus status() { return status; }
  public LocalDateTime expireTime() { return expireTime; }

  private static Map<String, String> copyAux(Map<String, String> source) {
    return source == null ? new HashMap<>() : new HashMap<>(source);
  }

  @Override
  protected void validateInvariants() {
    if (ownerType == null || ownerType.isBlank()) {
      throw new IllegalStateException("Credential.ownerType cannot be null or blank");
    }
    if (ownerId == null) {
      throw new IllegalStateException("Credential.ownerId cannot be null");
    }
    if (credentialType == null) {
      throw new IllegalStateException("Credential.credentialType cannot be null");
    }
    if (secretHash == null || secretHash.isBlank()) {
      throw new IllegalStateException("Credential.secretHash cannot be null or blank");
    }
    if (status == null) {
      throw new IllegalStateException("Credential.status cannot be null");
    }
  }
}
