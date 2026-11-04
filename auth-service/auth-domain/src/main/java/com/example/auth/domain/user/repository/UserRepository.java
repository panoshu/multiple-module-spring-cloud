package com.example.auth.domain.user.repository;

import com.example.core.types.contact.Mobile;
import com.example.shared.domain.repository.Repository;
import com.example.core.types.UserNo;
import com.example.auth.domain.user.aggregate.UserAggregate;

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
