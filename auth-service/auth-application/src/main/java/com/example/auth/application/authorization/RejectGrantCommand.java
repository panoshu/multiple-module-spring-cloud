package com.example.auth.application.authorization;

import com.example.core.types.UserNo;
import com.example.auth.types.GrantId;

public record RejectGrantCommand(GrantId grantId, UserNo operator) {
}
