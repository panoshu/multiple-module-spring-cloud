package com.pension.permission.domain.credential.valueobject.owner;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 凭证的持有对象。密码天然是"人证客户身份"，持有者只会是账号本身；
 * 但UKey这类物理凭证在企业年金场景里，实际持有者往往是客户(企业)或计划，
 * 不是具体某个人——这也是为什么Credential不能像之前那样写死一个accountId。
 */
public sealed interface CredentialOwner
  extends ValueObject
  permits UserCredentialOwner, CustomerCredentialOwner, PlanCredentialOwner {
}
