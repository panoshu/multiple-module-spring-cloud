package com.example.auth.application.channel;

import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.auth.types.SessionId;

public record SelectPlanCommand(SessionId sessionId, PlanNo planId, UserNo operator) {
}
