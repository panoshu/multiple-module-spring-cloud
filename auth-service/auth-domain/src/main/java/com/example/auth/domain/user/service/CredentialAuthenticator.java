package com.example.auth.domain.user.service;


import com.example.shared.exception.CommonError;
import com.example.shared.exception.DomainException;
import com.example.shared.domain.annotation.DomainService;
import com.example.auth.domain.credential.aggregate.Credential;
import com.example.auth.domain.credential.aggregate.PasswordCredential;
import com.example.auth.domain.credential.aggregate.UKeyCredential;
import com.example.auth.domain.credential.enumeration.CredentialStatus;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * 按凭证的具体类型分派到对应的认证策略。得益于Credential是sealed接口，
 * 这里的switch模式匹配是穷举的：以后新增凭证实现类型时，编译器会在这里提示需要处理新分支。
 * <p>
 * 校验前先检查状态是否ACTIVE——被撤销的凭证即使proof还对得上也不该通过，
 * 这是聚合根状态被真正"用起来"的地方，不只是摆设。
 */
@DomainService
@RequiredArgsConstructor
public final class CredentialAuthenticator {

  private final Map<Class<? extends Credential>, AuthenticationProvider> providers;

  public boolean authenticate(Credential credential, String proof) {
    if (credential.status() != CredentialStatus.ACTIVE) {
      return false;
    }
    AuthenticationProvider provider = switch (credential) {
      case PasswordCredential p -> providers.get(PasswordCredential.class);
      case UKeyCredential u -> providers.get(UKeyCredential.class);
    };
    if (provider == null) {
      throw new DomainException(CommonError.BAD_REQUEST)
        .withUserDetail("未注册该凭证类型的认证策略").withLogDetail("credential=" + credential.getClass());
    }
    return provider.authenticate(credential, proof);
  }
}
