package com.pension.permission.application.channel;


import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.identifier.id.UserNo;

public record OpenSessionCommand(UserNo accountId, AnnuityChannel channel) {
}
