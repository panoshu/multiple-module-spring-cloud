package com.example.auth.application.identity;

import com.example.core.types.UserNo;

public record FreezeAccountCommand(UserNo accountId, UserNo operator) {
}
