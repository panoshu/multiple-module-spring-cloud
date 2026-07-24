package com.example.shared.id.algorithm;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID v7 核心算法
 * <p>
 * 结构 (128 bits):
 * - unix_ts_ms (48 bits): 毫秒级时间戳
 * - ver (4 bits): 版本号 (0111 = 7)
 * - rand_a (12 bits): 随机数 A
 * - var (2 bits): 变体号 (10)
 * - rand_b (62 bits): 随机数 B
 */
public class UuidV7Algorithm {

  private static final SecureRandom RANDOM = new SecureRandom();

  public static UUID generate() {
    long timestamp = System.currentTimeMillis();

    // 1. 生成 16 字节的随机数作为基础
    byte[] value = new byte[16];
    RANDOM.nextBytes(value);

    // 2. 填充高 64 位 (Timestamp + Version)
    // 0-47: Timestamp (48 bits)
    value[0] = (byte) ((timestamp >> 40) & 0xFF);
    value[1] = (byte) ((timestamp >> 32) & 0xFF);
    value[2] = (byte) ((timestamp >> 24) & 0xFF);
    value[3] = (byte) ((timestamp >> 16) & 0xFF);
    value[4] = (byte) ((timestamp >> 8) & 0xFF);
    value[5] = (byte) (timestamp & 0xFF);

    // 48-51: Version (0111 = 7)
    // value[6] 的高4位设为 0111 (0x7)
    value[6] = (byte) ((value[6] & 0x0F) | 0x70);

    // 3. 填充低 64 位 (Variant)
    // 64-65: Variant (10xx)
    // value[8] 的高2位设为 10 (0x8)
    value[8] = (byte) ((value[8] & 0x3F) | 0x80);

    // 4. 转为 UUID 对象
    long msb = 0;
    long lsb = 0;
    for (int i = 0; i < 8; i++) msb = (msb << 8) | (value[i] & 0xFF);
    for (int i = 0; i < 8; i++) lsb = (lsb << 8) | (value[i + 8] & 0xFF);

    return new UUID(msb, lsb);
  }
}
