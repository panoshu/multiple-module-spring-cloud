package com.pension.permission.domain.credential.valueobject.owner;

import com.example.shared.identifier.id.UserNo;

/**
 * 凭证归属于具体某个账号(个人)——密码始终是这种，UKey如果是发给个人的也可以是这种
 */
public record UserCredentialOwner(
  UserNo userNo
) implements CredentialOwner {
}
