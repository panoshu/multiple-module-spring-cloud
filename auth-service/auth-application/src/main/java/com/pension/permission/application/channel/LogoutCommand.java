package com.pension.permission.application.channel;


import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SessionId;

public record LogoutCommand(SessionId sessionId, UserNo operator) {
}
