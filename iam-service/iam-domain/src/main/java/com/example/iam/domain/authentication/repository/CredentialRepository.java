package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.repository.Repository;

import java.util.List;

/**
 * 凭据 Repository 接口
 */
public interface CredentialRepository extends Repository<Credential, CredentialId> {

    /**
     * 根据所有者查找所有凭据
     *
     * @param ownerType 所有者类型（INTERNET_USER / HQ_USER / BRANCH_USER）
     * @param ownerId   所有者 ID
     */
    List<Credential> findByOwner(String ownerType, Long ownerId);

    /**
     * 根据所有者和凭据类型查找凭据
     */
    List<Credential> findByOwnerAndType(String ownerType, Long ownerId, CredentialType credentialType);
}
