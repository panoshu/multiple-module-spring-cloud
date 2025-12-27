package com.example.shared.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 外部服务响应基类
 * 核心能力：承载 HTTP 状态码和响应头，以及通用的原始响应体
 */
@Getter
@Setter
@ToString
public abstract class BaseExternalResponse implements Serializable {

  /**
   * HTTP 状态码 (如 200, 404)
   * 通常由拦截器或适配层填充
   */
  @JsonIgnore
  private Integer httpStatusCode;

  /**
   * 响应头
   */
  @JsonIgnore
  private Map<String, String> responseHeaders = new HashMap<>();

  /**
   * 原始响应体字符串 (可选，用于排查问题)
   */
  @JsonIgnore
  private String rawBody;

  public void addResponseHeader(String key, String value) {
    if (this.responseHeaders == null) {
      this.responseHeaders = new HashMap<>();
    }
    this.responseHeaders.put(key, value);
  }

  public String getHeader(String key) {
    return responseHeaders == null ? null : responseHeaders.get(key);
  }

  public Map<String, String> getHeaders() {
    return responseHeaders == null ? Collections.emptyMap() : Collections.unmodifiableMap(responseHeaders);
  }

  /**
   * 判断 HTTP 通讯是否成功 (非业务成功)
   */
  public boolean isHttpSuccess() {
    return httpStatusCode != null && httpStatusCode >= 200 && httpStatusCode < 300;
  }
}
