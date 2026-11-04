package com.example.auth.application.channel;


import com.example.core.types.Channel;
import com.example.core.types.UserNo;

public record OpenSessionCommand(UserNo accountId, Channel channel) {
}
