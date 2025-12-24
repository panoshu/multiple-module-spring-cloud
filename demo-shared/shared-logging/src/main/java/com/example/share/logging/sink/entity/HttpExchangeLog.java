package com.example.share.logging.sink.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "http_exchange_logs", indexes = {
  @Index(name = "idx_correlation_id", columnList = "correlationId"),
  @Index(name = "idx_created_time", columnList = "createdTime"),
  @Index(name = "idx_status_code", columnList = "statusCode"),
  @Index(name = "idx_complete", columnList = "complete")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Accessors(chain = true)
public class HttpExchangeLog {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private Long id;

  @Column(nullable = false, updatable = false, columnDefinition = "UUID")
  private UUID correlationId = UUID.randomUUID();

  @Column(nullable = false, length = 50)
  private String remote;

  @Column(nullable = false, length = 10)
  private String method;

  @Column(nullable = false, length = 2000)
  private String uri;

  @Column(nullable = false)
  private int statusCode;

  @Column(nullable = false)
  private long durationMillis;

  @Column(columnDefinition = "TEXT")
  private String requestHeaders;

  @Column(columnDefinition = "TEXT")
  private String requestContent;

  @Column(columnDefinition = "TEXT")
  private String responseHeaders;

  @Column(columnDefinition = "TEXT")
  private String responseContent;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdTime = LocalDateTime.now();

  @Column(length = 100)
  private String clientInfo;

  @Column(length = 50)
  private String ip;

  @Column(length = 500)
  private String userAgent;

  @Column(nullable = false)
  private boolean truncated = false;

  @Column(length = 100)
  private String contentType;

  @Column(nullable = false)
  private boolean complete = false;
}
