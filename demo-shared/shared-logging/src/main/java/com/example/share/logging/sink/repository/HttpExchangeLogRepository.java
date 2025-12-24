package com.example.share.logging.sink.repository;

import com.example.share.logging.sink.entity.HttpExchangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface HttpExchangeLogRepository extends
  JpaRepository<HttpExchangeLog, Long>,
  JpaSpecificationExecutor<HttpExchangeLog> {

  List<HttpExchangeLog> findByCorrelationId(UUID correlationId);
  boolean existsByCorrelationId(UUID correlationId);
  List<HttpExchangeLog> findByCompleteFalseAndCreatedTimeBefore(LocalDateTime cutoffTime);

  @Modifying
  @Transactional
  @Query("UPDATE HttpExchangeLog l SET " +
    "l.statusCode = :statusCode, " +
    "l.durationMillis = :durationMillis, " +
    "l.responseHeaders = :responseHeaders, " +
    "l.responseContent = :responseContent, " +
    "l.clientInfo = :clientInfo, " +
    "l.ip = :ip, " +
    "l.userAgent = :userAgent, " +
    "l.complete = true " +
    "WHERE l.correlationId = :correlationId AND l.complete = false")
  int updateResponseData(
    @Param("correlationId") UUID correlationId,
    @Param("statusCode") int statusCode,
    @Param("durationMillis") long durationMillis,
    @Param("responseHeaders") String responseHeaders,
    @Param("responseContent") String responseContent,
    @Param("clientInfo") String clientInfo,
    @Param("ip") String ip,
    @Param("userAgent") String userAgent
  );

  @Modifying
  @Transactional
  @Query("DELETE FROM HttpExchangeLog WHERE createdTime < :beforeTime")
  int deleteOldLogs(@Param("beforeTime") LocalDateTime beforeTime);
}
