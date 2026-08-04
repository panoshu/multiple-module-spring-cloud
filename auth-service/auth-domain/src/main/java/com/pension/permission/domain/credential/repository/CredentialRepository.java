package com.pension.permission.domain.credential.repository;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.repository.Repository;
import com.pension.permission.domain.credential.aggregate.Credential;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.types.CredentialId;

import java.util.List;
import java.util.Optional;

/**
 * CredentialRepository
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 21:35
 */
public interface CredentialRepository extends Repository<Credential, CredentialId> {

  /**
   * 查询持有者的全部凭证
   */
  List<Credential> findByOwner(
    CredentialOwner owner
  );


  /**
   * 查询持有者指定类型的凭证
   * 例如：
   * User + Password
   * Plan + UKey
   */
  Optional<Credential> findByOwnerAndType(
    CredentialOwner owner,
    CredentialType type
  );


  /**
   * 查询持有者是否存在指定类型凭证
   */
  default boolean existsByOwnerAndType(
    CredentialOwner owner,
    CredentialType type
  ) {

    return findByOwnerAndType(owner, type)
      .isPresent();

  }


  /**
   * 查询当前可用于认证的凭证
   */
  List<Credential> findActiveCredentials(
    CredentialOwner owner
  );


  /**
   * 根据渠道查询可用凭证
   * 例如：
   * WEB登录
   * APP登录
   * 银企直连
   */
  List<Credential> findUsableCredentials(
    CredentialOwner owner,
    AnnuityChannel channel
  );


  /**
   * 根据凭证类型批量查询
   * 管理场景使用
   */
  List<Credential> findByType(
    CredentialType type
  );

}
