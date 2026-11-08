package com.example.auth.application.identity;

import com.example.core.types.UserNo;

public record ActivateAccountCommand(UserNo accountId, UserNo operator) {
}
