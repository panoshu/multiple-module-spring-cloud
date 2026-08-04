package com.pension.permission.application.identity;

import com.example.shared.identifier.id.UserNo;

public record ActivateAccountCommand(UserNo accountId, UserNo operator) {
}
