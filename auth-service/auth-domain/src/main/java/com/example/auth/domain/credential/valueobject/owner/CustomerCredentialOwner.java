package com.example.auth.domain.credential.valueobject.owner;

import com.example.core.types.CustomerNo;

/**
 * 凭证归属于某个客户(企业)——典型场景：企业统一持有的UKey
 */
public record CustomerCredentialOwner(CustomerNo customerNo) implements CredentialOwner {
}
