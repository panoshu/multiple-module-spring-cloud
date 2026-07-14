package core.domain.outbound;

/**
 * 跨文件全局防重校验端口 (由基础设施层的 Redis 或 DB 实现)
 */
public interface CrossFileUniqueValidatorPort {

  /**
   * 检查并记录唯一键
   *
   * @param batchId   批次号 (如业务申请单ID)
   * @param uniqueKey 组合特征值 (如 "张三|身份证|110105")
   * @param fileName  当前解析的文件名 (用于报错提示)
   * @param rowIndex  当前行号
   * @return 如果已经存在，返回冲突的文件名和行号；如果不存在，则记录并返回 null。
   */
  ConflictInfo checkAndAdd(String batchId, String uniqueKey, String fileName, int rowIndex);

  record ConflictInfo(String existFileName, int existRowIndex) {
  }
}
// 基础设施层实现 (伪代码示例)
// public class RedisCrossFileValidatorAdapter implements CrossFileUniqueValidatorPort {
//  private final RedisTemplate redisTemplate;
//
//  @Override
//  public ConflictInfo checkAndAdd(String fileName, String uniqueKey, String fileName, int rowIndex) {
//    String redisKey = "excel_unique_check:" + fileName;
//    String locationValue = fileName + " 的第 " + rowIndex + " 行";
//
//    // 尝试放入 Redis Hash，如果该 uniqueKey 已经存在，putIfAbsent 会返回 false
//    Boolean isNew = redisTemplate.opsForHash().putIfAbsent(redisKey, uniqueKey, locationValue);
//
//    if (Boolean.FALSE.equals(isNew)) {
//      // 发生冲突！去 Redis 里把已经存在的那条记录的位置取出来
//      String existLocation = (String) redisTemplate.opsForHash().get(redisKey, uniqueKey);
//      return new ConflictInfo(existLocation, -1);
//    }
//
//    // 顺手设置个过期时间（比如 2 小时），防止垃圾数据堆积
//    redisTemplate.expire(redisKey, 2, TimeUnit.HOURS);
//    return null; // 没有冲突
//  }
//}
