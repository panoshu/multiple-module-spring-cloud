package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.HqUser;
import com.example.iam.types.HqUserId;
import com.example.shared.domain.repository.Repository;

import java.util.Optional;

/**
 * 总部渠道用户 Repository 接口
 */
public interface HqUserRepository extends Repository<HqUser, HqUserId> {

    /**
     * 根据登录名查找用户
     */
    Optional<HqUser> findByLoginName(String loginName);
}
