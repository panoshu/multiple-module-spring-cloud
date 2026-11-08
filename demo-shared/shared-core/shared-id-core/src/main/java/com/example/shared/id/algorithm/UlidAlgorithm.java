package com.example.shared.id.algorithm;

import java.security.SecureRandom;
import java.util.Random;

/**
 * ULID 核心算法
 * <p>
 * 结构 (26 chars):
 * - Timestamp (48 bits) -> 10 chars
 * - Randomness (80 bits) -> 16 chars
 * 编码: Crockford Base32
 *
 * <p><b>可见性纪律（治理）</b>：本类是 IdType.ULID 策略的内部实现，
 * 业务代码一律通过 {@code IdService.nextId(XxxId.class)}（shared-id-starter 门面）
 * 获取 ID——直接调用将绕过策略注册、启动校验与格式化，且换策略时无法平滑迁移
 * （当前 file-service 4 处直调即为待收编存量，见 spec 审计记录）.
 */
public class UlidAlgorithm {

  private static final char[] ENCODING_CHARS = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final Random RANDOM = new SecureRandom();

  public static String generate() {
    return generate(System.currentTimeMillis());
  }

  public static String generate(long timestamp) {
    char[] buffer = new char[26];

    // 1. 编码时间戳 (48 bits -> 10 chars)
    // 每次取 5 bit 进行 Base32 映射
    buffer[0] = ENCODING_CHARS[(int) ((timestamp >>> 45) & 0x1F)];
    buffer[1] = ENCODING_CHARS[(int) ((timestamp >>> 40) & 0x1F)];
    buffer[2] = ENCODING_CHARS[(int) ((timestamp >>> 35) & 0x1F)];
    buffer[3] = ENCODING_CHARS[(int) ((timestamp >>> 30) & 0x1F)];
    buffer[4] = ENCODING_CHARS[(int) ((timestamp >>> 25) & 0x1F)];
    buffer[5] = ENCODING_CHARS[(int) ((timestamp >>> 20) & 0x1F)];
    buffer[6] = ENCODING_CHARS[(int) ((timestamp >>> 15) & 0x1F)];
    buffer[7] = ENCODING_CHARS[(int) ((timestamp >>> 10) & 0x1F)];
    buffer[8] = ENCODING_CHARS[(int) ((timestamp >>> 5) & 0x1F)];
    buffer[9] = ENCODING_CHARS[(int) (timestamp & 0x1F)];

    // 2. 生成并编码随机数 (80 bits -> 16 chars)
    byte[] randomness = new byte[10];
    RANDOM.nextBytes(randomness);

    // 将 10 byte (80 bit) 的随机数映射为 16 个 Base32 字符
    // 这里为了逻辑清晰，采用逐块位移的方式 (性能可满足绝大多数场景)
    // 也可以使用 long 来加速位运算
    long rand1 = ((long) randomness[0] & 0xFF) << 32 |
      ((long) randomness[1] & 0xFF) << 24 |
      ((long) randomness[2] & 0xFF) << 16 |
      ((long) randomness[3] & 0xFF) << 8 |
      ((long) randomness[4] & 0xFF);

    long rand2 = ((long) randomness[5] & 0xFF) << 32 |
      ((long) randomness[6] & 0xFF) << 24 |
      ((long) randomness[7] & 0xFF) << 16 |
      ((long) randomness[8] & 0xFF) << 8 |
      ((long) randomness[9] & 0xFF);

    encodeRandom(buffer, 10, rand1); // 填充 8 个字符
    encodeRandom(buffer, 18, rand2); // 填充 8 个字符

    return new String(buffer);
  }

  private static void encodeRandom(char[] buffer, int offset, long value) {
    for (int i = 0; i < 8; i++) {
      // 从低位开始填，或者从高位开始填均可，只要保证 decode 一致
      // ULID 标准通常是大端序，这里我们在 generate 时已经是大端序构建的 long
      // 每次取高 5 位
      int index = (int) ((value >>> (35 - i * 5)) & 0x1F);
      buffer[offset + i] = ENCODING_CHARS[index];
    }
  }
}
