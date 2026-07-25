package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.repository.Repository;

import java.util.List;

/**
 * 二次授权会话 Repository 接口
 */
public interface SecondaryAuthSessionRepository extends Repository<SecondaryAuthSession, SecondaryAuthSessionId> {

    /**
     * 查询指定网点柜员的所有未过期/未撤销的有效会话
     */
    List<SecondaryAuthSession> findActiveByBranchUser(BranchUserId branchUserId);
}
