package com.pension.permission.application.channel;

import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.SessionId;

public record SelectPlanCommand(SessionId sessionId, PlanNo planId, UserNo operator) {
}
