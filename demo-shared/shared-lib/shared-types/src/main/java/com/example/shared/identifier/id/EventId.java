package com.example.shared.identifier.id;


import com.example.shared.identifier.contract.IdDefinition;
import com.example.shared.identifier.contract.IdType;
import com.example.shared.identifier.contract.Identifier;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 领域事件ID (纯 JDK 实现 ULID，无第三方依赖)
 *
 * @author hupan
 */
@IdDefinition(type = IdType.ULID)
public record EventId(String value) implements Identifier<String> {

  private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final int ULID_LENGTH = 26;
  private static final int TIME_PART_LENGTH = 10;
  private static final int RANDOM_PART_LENGTH = 16;
  // 使用 SecureRandom 保证即使在高并发下也不会产生碰撞
  private static final Random RANDOM = new SecureRandom();

  public EventId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("EventId cannot be null or blank.");
    }
  }

  /**
   * 领域层专用：无依赖地生成新的 EventId
   */
  public static EventId generate() {
    return new EventId(generateUlid());
  }

  /**
   * 纯 JDK 实现 ULID 生成逻辑
   */
  private static String generateUlid() {
    long time = System.currentTimeMillis();
    byte[] randomBytes = new byte[10];
    RANDOM.nextBytes(randomBytes);

    char[] chars = new char[ULID_LENGTH];

    // 1. 编码时间戳 (48 bits -> 10 chars)
    long t = time;
    for (int i = TIME_PART_LENGTH - 1; i >= 0; i--) {
      chars[i] = ENCODING[(int) (t & 0x1F)]; // 取低 5 位
      t >>>= 5; // 无符号右移 5 位
    }

    // 2. 编码随机数 (80 bits -> 16 chars)
    // 将 10 个 byte 拼接后按 5 bit 切分
    // 为了避免处理跨越 byte 边界的麻烦，我们使用位运算逐个提取
    int buffer = randomBytes[0];
    int bufferBits = 8;
    int randomIndex = 1;

    for (int i = TIME_PART_LENGTH; i < ULID_LENGTH; i++) {
      if (bufferBits < 5) {
        // 缓冲区不足 5 位，读入下一个 byte
        if (randomIndex < randomBytes.length) {
          buffer = (buffer << 8) | (randomBytes[randomIndex++] & 0xFF);
          bufferBits += 8;
        } else {
          // 理论上 10 bytes (80 bits) 足够填充 16 chars (80 bits)，不会走此分支
          buffer = buffer << (5 - bufferBits);
          bufferBits = 5;
        }
      }
      chars[i] = ENCODING[(buffer >>> (bufferBits - 5)) & 0x1F];
      bufferBits -= 5;
    }

    return new String(chars);
  }
}
