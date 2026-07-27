package com.example.iam.application.service;

import com.example.iam.api.command.ChangeCredentialCommand;
import com.example.iam.api.command.CreateCredentialCommand;
import com.example.iam.api.command.RevokeCredentialCommand;
import com.example.iam.api.dto.CredentialDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListCredentialsQuery;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 凭据管理应用服务。
 *
 * <p>负责用户凭据(密码/UKey/动态令牌)的创建、修改、撤销与查询编排。
 * 密码类型凭据通过 {@link PasswordEncryptorPort} 加密后持久化,
 * 其他类型凭据由前端/外部加密后直接存储。
 *
 * <p>本服务仅编排业务流程,凭据状态机校验、密文格式校验由 Credential 聚合根负责。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CredentialAppService {

  private final CredentialRepository credentialRepository;
  private final PasswordEncryptorPort passwordEncryptorPort;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 创建凭据。
   *
   * <p>密码类型凭据通过 {@link PasswordEncryptorPort} 加密;
   * 其他类型凭据(UKey/动态令牌)直接使用命令中传入的密文。
   *
   * @param command 创建凭据命令
   * @return 新建凭据 ID
   */
  @Transactional
  public IdResponseDTO create(CreateCredentialCommand command) {
    CredentialType credentialType = parseCredentialType(command.credentialType());
    String secretHash = encryptIfNeeded(credentialType, command.secretHash());

    CredentialId credentialId = idService.nextLongId(CredentialId.class, "IAM_CREDENTIAL");
    UserNo operator = UserNo.of(command.operator());

    Credential credential = Credential.create(
        credentialId, command.ownerType(), command.ownerId(),
        credentialType, secretHash, command.salt(), command.auxData(),
        command.expireTime(), operator);

    credentialRepository.save(credential);
    publishEvents(credential);

    log.info("凭据创建成功: credentialId={}, ownerType={}, ownerId={}, type={}",
        credentialId.value(), command.ownerType(), command.ownerId(), credentialType);
    return new IdResponseDTO(credentialId.value());
  }

  /**
   * 修改凭据密文(密码修改、UKey 轮换等)。
   *
   * @param command 修改凭据命令
   */
  @Transactional
  public void change(ChangeCredentialCommand command) {
    Credential credential = loadCredentialOrThrow(command.credentialId());
    UserNo operator = UserNo.of(command.operator());
    String newSecretHash = encryptIfNeeded(credential.credentialType(), command.newSecretHash());
    credential.change(newSecretHash, command.newSalt(), command.newAuxData(), operator);
    credentialRepository.save(credential);
    publishEvents(credential);
    log.info("凭据修改成功: credentialId={}", command.credentialId());
  }

  /**
   * 撤销凭据(终态,不可恢复)。
   *
   * @param command 撤销凭据命令
   */
  @Transactional
  public void revoke(RevokeCredentialCommand command) {
    Credential credential = loadCredentialOrThrow(command.credentialId());
    UserNo operator = UserNo.of(command.operator());
    credential.markRevoked(operator);
    credentialRepository.save(credential);
    publishEvents(credential);
    log.info("凭据撤销成功: credentialId={}", command.credentialId());
  }

  /**
   * 凭据列表分页查询。
   *
   * @param query 列表查询
   * @return 分页结果
   */
  @Transactional(readOnly = true)
  public PageData<CredentialDTO> list(ListCredentialsQuery query) {
    List<Credential> all = credentialRepository.loadAll();
    List<Credential> filtered = all.stream()
        .filter(c -> matchesOwner(c, query.ownerId(), query.ownerType()))
        .filter(c -> matchesType(c, query.credentialType()))
        .filter(c -> matchesStatus(c, query.status()))
        .toList();
    return paginate(filtered, query.pageQuery());
  }

  /**
   * 加载凭据或抛出业务异常。
   */
  private Credential loadCredentialOrThrow(Long credentialId) {
    return credentialRepository.load(CredentialId.of(credentialId))
        .orElseThrow(() -> new BusinessException(IamAuthErrorCode.CREDENTIAL_NOT_FOUND)
            .withUserDetail("凭据不存在")
            .withContext("credentialId", credentialId));
  }

  /**
   * 密码类型凭据加密;其他类型原样返回。
   */
  private String encryptIfNeeded(CredentialType type, String secret) {
    if (type == CredentialType.PASSWORD) {
      return passwordEncryptorPort.encrypt(secret);
    }
    return secret;
  }

  /**
   * 解析凭据类型,无效时抛业务异常。
   */
  private CredentialType parseCredentialType(String credentialType) {
    try {
      return CredentialType.valueOf(
          Objects.requireNonNull(credentialType, "credentialType cannot be null"));
    } catch (IllegalArgumentException e) {
      throw new BusinessException(IamAuthErrorCode.CREDENTIAL_TYPE_NOT_SUPPORTED)
          .withUserDetail("不支持的凭据类型: " + credentialType)
          .withContext("credentialType", credentialType);
    }
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  private void publishEvents(Credential credential) {
    credential.getDomainEvents().forEach(eventBus::publish);
    credential.clearDomainEvents();
  }

  /**
   * 列表分页切片(简化实现)。
   */
  private PageData<CredentialDTO> paginate(List<Credential> credentials, PageQuery pageQuery) {
    int total = credentials.size();
    int from = Math.min(pageQuery.startPos(), total);
    int to = Math.min(from + pageQuery.pageSize(), total);
    List<CredentialDTO> items = credentials.subList(from, to).stream()
        .map(this::toDTO)
        .toList();
    return new PageData<>(total, from, items.size(), to < total, items);
  }

  private boolean matchesOwner(Credential c, Long ownerId, String ownerType) {
    boolean ownerMatches = ownerId == null || ownerId.equals(c.ownerId());
    boolean typeMatches = ownerType == null || ownerType.isBlank()
        || ownerType.equals(c.ownerType());
    return ownerMatches && typeMatches;
  }

  private boolean matchesType(Credential c, String credentialType) {
    if (credentialType == null || credentialType.isBlank()) {
      return true;
    }
    return c.credentialType() != null && c.credentialType().name().equals(credentialType);
  }

  private boolean matchesStatus(Credential c, String status) {
    if (status == null || status.isBlank()) {
      return true;
    }
    CredentialStatus currentStatus = c.status();
    return currentStatus != null && currentStatus.name().equals(status);
  }

  /**
   * 领域对象转 DTO(出于安全考虑不返回 secretHash/salt)。
   */
  private CredentialDTO toDTO(Credential c) {
    return new CredentialDTO(
        c.id().value(),
        c.ownerType(),
        c.ownerId(),
        c.credentialType() != null ? c.credentialType().name() : null,
        c.status() != null ? c.status().name() : null,
        c.expireTime(),
        c.auxData(),
        c.createdAt(),
        c.updatedAt(),
        c.version() != null ? c.version().value() : null
    );
  }
}
