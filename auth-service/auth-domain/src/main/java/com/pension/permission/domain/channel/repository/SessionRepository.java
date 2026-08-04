package com.pension.permission.domain.channel.repository;


import com.example.shared.domain.repository.Repository;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.types.SessionId;


public interface SessionRepository extends Repository<Session, SessionId> {
}
