package com.example.shared.web.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 客户端 IP 获取工具类
 * <p>
 * 处理多级代理、Nginx转发、Cloudflare等场景下的真实IP解析
 */
@UtilityClass // Lombok 注解，自动私有化构造器，所有方法静态
public class ClientIpUtils {

  private static final String UNKNOWN = "unknown";
  private static final String COMMA = ",";
  private static final String LOCAL_IPV4 = "127.0.0.1";
  private static final String LOCAL_IPV6 = "0:0:0:0:0:0:0:1";

  /**
   * 获取客户端真实 IP
   * <p>
   * 优先级：
   * 1. X-Forwarded-For (标准代理头)
   * 2. Proxy-Client-IP (Apache HTTP Server 模块)
   * 3. WL-Proxy-Client-IP (WebLogic)
   * 4. HTTP_CLIENT_IP
   * 5. HTTP_X_FORWARDED_FOR
   * 6. X-Real-IP (Nginx)
   * 7. getRemoteAddr (直接连接 IP)
   *
   * @param request HttpServletRequest
   * @return 真实 IP 字符串
   */
  public static String getRemoteIp(HttpServletRequest request) {
    if (request == null) {
      return UNKNOWN;
    }

    String ip = request.getHeader("X-Forwarded-For");

    if (isInvalid(ip)) {
      ip = request.getHeader("Proxy-Client-IP");
    }
    if (isInvalid(ip)) {
      ip = request.getHeader("WL-Proxy-Client-IP");
    }
    if (isInvalid(ip)) {
      ip = request.getHeader("HTTP_CLIENT_IP");
    }
    if (isInvalid(ip)) {
      ip = request.getHeader("HTTP_X_FORWARDED_FOR");
    }
    // 很多 Nginx 配置使用 X-Real-IP
    if (isInvalid(ip)) {
      ip = request.getHeader("X-Real-IP");
    }

    // 回退到基础连接 IP
    if (isInvalid(ip)) {
      ip = request.getRemoteAddr();
      // 如果是本机访问，根据网卡获取本机真实 IP
      if (LOCAL_IPV4.equals(ip) || LOCAL_IPV6.equals(ip)) {
        try {
          InetAddress inetAddress = InetAddress.getLocalHost();
          ip = inetAddress.getHostAddress();
        } catch (UnknownHostException e) {
          // 忽略，保持 127.0.0.1
        }
      }
    }

    // 处理多级代理的情况：X-Forwarded-For: client, proxy1, proxy2
    // 第一个非 unknown 的才是真实 IP
    if (ip != null && ip.contains(COMMA)) {
      String[] ips = ip.split(COMMA);
      for (String subIp : ips) {
        if (!isInvalid(subIp)) {
          ip = subIp;
          break;
        }
      }
    }

    // 截断可能存在的 IPv6 Zone ID (例如 fe80::1%eth0)
    if (ip != null && ip.contains("%")) {
      ip = ip.split("%")[0];
    }

    return ip != null ? ip.trim() : UNKNOWN;
  }

  private static boolean isInvalid(String ip) {
    return !StringUtils.hasLength(ip) || UNKNOWN.equalsIgnoreCase(ip);
  }
}
