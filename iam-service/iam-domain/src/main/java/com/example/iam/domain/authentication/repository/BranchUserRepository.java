package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.BranchUser;
import com.example.iam.types.BranchUserId;
import com.example.shared.domain.repository.Repository;

import java.util.Optional;

/**
 * 网点渠道用户 Repository 接口
 */
public interface BranchUserRepository extends Repository<BranchUser, BranchUserId> {

    /**
     * 根据登录名查找用户
     */
    Optional<BranchUser> findByLoginName(String loginName);
}
