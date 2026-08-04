package com.pension.permission.domain.credential.valueobject.owner;

import com.example.shared.identifier.id.PlanNo;

/**
 * 凭证归属于某个计划——典型场景：按计划单独发放的UKey
 */
public record PlanCredentialOwner(PlanNo planNo) implements CredentialOwner {
}
