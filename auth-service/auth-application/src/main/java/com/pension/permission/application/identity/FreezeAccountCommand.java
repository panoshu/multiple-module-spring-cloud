package com.pension.permission.application.identity;

import com.example.shared.identifier.id.UserNo;

public record FreezeAccountCommand(UserNo accountId, UserNo operator) {
}
