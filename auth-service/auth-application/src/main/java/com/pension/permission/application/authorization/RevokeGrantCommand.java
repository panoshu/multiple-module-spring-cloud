package com.pension.permission.application.authorization;


import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.GrantId;

public record RevokeGrantCommand(GrantId grantId, UserNo operator) {
}
