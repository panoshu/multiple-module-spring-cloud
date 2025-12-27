package com.example.shared.core.trace.context;

import org.slf4j.MDC;
import java.util.Map;

/**
 * 业务上下文工具类
 * 封装 MDC 操作，避免硬编码 Key
 */
public class BizContext {
  public static final String KEY_BIZ_ID = "bizId";
  public static final String KEY_BATCH_ID = "batchId";
  public static final String KEY_JNL_NO = "jnlNo";

  // —————— Biz ID ——————
  public static void setBizId(String val) {
    if (val != null) MDC.put(KEY_BIZ_ID, val);
  }

  public static String getBizId() {
    return MDC.get(KEY_BIZ_ID);
  }

  // —————— Batch ID (修复：补全 Get 方法) ——————
  public static void setBatchId(String val) {
    if (val != null) MDC.put(KEY_BATCH_ID, val);
  }

  public static String getBatchId() { // 【新增】
    return MDC.get(KEY_BATCH_ID);
  }

  // —————— Jnl No (修复：补全 Get 方法) ——————
  public static void setJnlNo(String val) {
    if (val != null) MDC.put(KEY_JNL_NO, val);
  }

  public static String getJnlNo() { // 【新增】
    return MDC.get(KEY_JNL_NO);
  }

  // —————— 通用工具 ——————
  public static void clear() {
    MDC.remove(KEY_BIZ_ID);
    MDC.remove(KEY_BATCH_ID);
    MDC.remove(KEY_JNL_NO);
  }

  public static Map<String, String> getCopyOfContextMap() {
    return MDC.getCopyOfContextMap();
  }

  public static void setContextMap(Map<String, String> contextMap) {
    if (contextMap != null) {
      MDC.setContextMap(contextMap);
    } else {
      MDC.clear();
    }
  }
}
