package com.example.shared.id.algorithm;

/**
 * 雪花算法 ID 生成器（shared-id-core 内部算法，SNOWFLAKE 策略的实现底座）.
 *
 * <p>布局（标准 64 bit）：1 bit 符号位(0) + 41 bit 毫秒时间戳（约 69 年）+
 * 10 bit 机器 ID（0-1023）+ 12 bit 序列号（同毫秒 4096 个）.
 *
 * <p>时钟回拨防护：检测到回拨时抛出 {@link IllegalStateException}（fail-fast，
 * 不静默等待——分布式环境应保证 NTP 平滑同步）.
 *
 * <p><b>可见性纪律</b>：本类为策略内部实现，业务代码一律通过
 * {@code IdService.nextId(XxxId.class)}（shared-id-starter 门面）获取 ID；
 * 直接调用本类将绕过策略注册、启动校验与格式化（参见 ArchUnit 不变量 8）.
 *
 * @author shared-id-core
 */
public final class SnowflakeAlgorithm {

  private static final long TWEAK_EPOCH = 1_780_000_000_000L;
  private static final long MACHINE_ID_BITS = 10L;
  private static final long SEQUENCE_BITS = 12L;
  private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
  private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;
  private static final long TIMESTAMP_SHIFT = MACHINE_ID_BITS + SEQUENCE_BITS;
  private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;

  private final long machineId;
  private long lastTimestamp = -1L;
  private long sequence = 0L;

  public SnowflakeAlgorithm(long machineId) {
    if (machineId < 0 || machineId > MAX_MACHINE_ID) {
      throw new IllegalArgumentException(
        "worker-id 必须在 0-" + MAX_MACHINE_ID + " 范围内: " + machineId);
    }
    this.machineId = machineId;
  }

  /**
   * 生成下一个 64 bit 雪花 ID（线程安全）.
   */
  public synchronized long nextId() {
    long timestamp = System.currentTimeMillis();
    if (timestamp < lastTimestamp) {
      throw new IllegalStateException(
        "时钟回拨: 当前=" + timestamp + ", 上次=" + lastTimestamp);
    }
    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) & SEQUENCE_MASK;
      if (sequence == 0) {
        // 当前毫秒序列耗尽，自旋到下一毫秒
        timestamp = tilNextMillis(lastTimestamp);
      }
    } else {
      sequence = 0L;
    }
    lastTimestamp = timestamp;
    return ((timestamp - TWEAK_EPOCH) << TIMESTAMP_SHIFT)
      | (machineId << MACHINE_ID_SHIFT)
      | sequence;
  }

  private long tilNextMillis(long lastTimestamp) {
    long timestamp = System.currentTimeMillis();
    while (timestamp <= lastTimestamp) {
      timestamp = System.currentTimeMillis();
    }
    return timestamp;
  }
}
