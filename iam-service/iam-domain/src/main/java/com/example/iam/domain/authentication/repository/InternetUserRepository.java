package com.example.iam.domain.authentication.repository;

import com.example.iam.domain.authentication.aggregate.root.InternetUser;
import com.example.iam.types.InternetUserId;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.CustomerNo;

import java.util.List;
import java.util.Optional;

/**
 * 网上渠道用户 Repository 接口
 */
public interface InternetUserRepository extends Repository<InternetUser, InternetUserId> {

    /**
     * 根据登录名查找用户
     */
    Optional<InternetUser> findByLoginName(String loginName);

    /**
     * 根据客户编号查找该客户下的所有经办人
     */
    List<InternetUser> findByCustomerNo(CustomerNo customerNo);
}
