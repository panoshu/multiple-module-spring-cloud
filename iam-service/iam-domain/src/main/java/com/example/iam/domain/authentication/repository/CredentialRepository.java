package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 凭据聚合根仓储接口。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public interface CredentialRepository extends Repository<Credential, CredentialId> {

  /**
   * 根据归属查找有效凭据。
   *
   * @param ownerId    归属实体 ID(User.id)
   * @param ownerType  归属类型
   * @param credentialType 凭据类型
   * @return 凭据(可能为空)
   */
  Optional<Credential> findActive(Long ownerId, String ownerType, CredentialType credentialType);

  /**
   * 查询用户的所有有效凭据(用于撤销/审计场景)。
   *
   * @param ownerId   归属实体 ID
   * @param ownerType 归属类型
   * @return 凭据列表(可能为空)
   */
  List<Credential> findAllByOwner(Long ownerId, String ownerType);
}
