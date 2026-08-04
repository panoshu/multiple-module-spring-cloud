package com.pension.permission.application.authorization;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.GrantId;

public record RejectGrantCommand(GrantId grantId, UserNo operator) {
}
