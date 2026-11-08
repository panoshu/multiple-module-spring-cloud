package com.example.auth.application.channel;


import com.example.core.types.UserNo;
import com.example.auth.types.SessionId;

public record LogoutCommand(SessionId sessionId, UserNo operator) {
}
