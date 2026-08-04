package com.pension.permission.domain.user.repository;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.user.aggregate.UserAggregate;

import java.util.Optional;

/**
 * UserRepository
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/2 09:31
 */
public interface UserRepository extends Repository<UserAggregate, UserNo> {
  Optional<UserAggregate> findByMobile(Mobile mobile);
}
