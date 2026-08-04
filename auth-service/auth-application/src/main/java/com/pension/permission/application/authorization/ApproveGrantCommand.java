package com.pension.permission.application.authorization;


import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.GrantId;

public record ApproveGrantCommand(GrantId grantId, UserNo operator) {
}
