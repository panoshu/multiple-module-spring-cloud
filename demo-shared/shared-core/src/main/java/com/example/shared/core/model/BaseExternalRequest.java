package com.example.shared.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 外部服务请求基类
 * 核心能力：携带通用的请求头（如 TraceId, Token）
 */
@Getter
@Setter
public abstract class BaseExternalRequest<T extends BaseExternalRequest<T>> implements Serializable {

  /**
   * 请求头容器
   * 标记 @JsonIgnore 防止被序列化到 JSON Body 中
   * (Retrofit 传 Body 时通常只需要业务字段)
   */
  @JsonIgnore
  private Map<String, String> headers = new HashMap<>();

  /**
   * 便捷添加 Header
   */
  @SuppressWarnings("unchecked")
  public T addHeader(String key, String value) {
    if (this.headers == null) {
      this.headers = new HashMap<>();
    }
    this.headers.put(key, value);
    return (T) this;
  }

  public String getHeader(String key) {
    return headers == null ? null : headers.get(key);
  }

  /**
   * 获取所有 Header (Retrofit @HeaderMap 需要)
   */
  public Map<String, String> getHeaders() {
    return headers == null ? Collections.emptyMap() : Collections.unmodifiableMap(headers);
  }
}
