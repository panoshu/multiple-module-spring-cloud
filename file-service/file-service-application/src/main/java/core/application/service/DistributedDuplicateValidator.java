// 文件: src/main/java/core/application/service/DistributedDuplicateValidator.java
package core.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import core.domain.model.ErrorPhase;
import core.domain.model.ErrorRecord;
import core.domain.model.ParseResult;
import core.domain.model.ParseStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分布式/跨请求异步全局防重校验器
 */
@Service
public class DistributedDuplicateValidator {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper mapper = new ObjectMapper();

  public DistributedDuplicateValidator(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  /**
   * 对引擎解析出的单文件 JSON 进行 Redis 全局检测
   *
   * @param batchId    业务批次号 (前端透传，标识同一次业务申请)
   * @param result     当前文件引擎解析出的结果
   * @param uniqueKeys 参与查重的联合主键 (如 name, idType, idNo)
   * @param fileName   当前文件名 (用于提示)
   * @return 经过全局校验后，可能追加了新错误信息的 ParseResult
   */
  public ParseResult validateAndRecord(String batchId, ParseResult result, List<String> uniqueKeys, String fileName) {
    if (uniqueKeys == null || uniqueKeys.isEmpty() || result.jsonPayload() == null) {
      return result;
    }

    List<ErrorRecord> additionalErrors = new ArrayList<>(result.errors());
    boolean foundDuplicate = false;

    // 业务缓存 Key，设定过期时间(如：业务申请有效生命周期为 24 小时)
    String redisHashKey = "EXCEL_CHECK:" + batchId;

    try {
      JsonNode details = mapper.readTree(result.jsonPayload()).path("details");
      if (details.isArray()) {
        for (JsonNode row : details) {
          // 1. 组装联合主键 (例如: "张三|身份证|11111")
          String combinedKey = uniqueKeys.stream()
            .map(k -> row.path(k).asText("null"))
            .collect(Collectors.joining("|"));

          int rowIndex = row.path("_meta").path("rowIndex").asInt(-1);
          String currentLocation = String.format("[%s-第%d行]", fileName, rowIndex);

          // 2. Redis 原子操作: putIfAbsent (底层是 HSETNX)
          // 如果 key 不存在则设值并返回 true；如果已存在则不操作并返回 false。完美解决并发问题！
          Boolean isNew = redisTemplate.opsForHash().putIfAbsent(redisHashKey, combinedKey, currentLocation);

          if (Boolean.FALSE.equals(isNew)) {
            // 3. 发现重复！去 Redis 查出到底是被谁占用了
            String existingLocation = (String) redisTemplate.opsForHash().get(redisHashKey, combinedKey);
            foundDuplicate = true;

            String errorMsg = String.format("与之前上传的数据发生全局重复！特征值: %s。冲突源: %s",
              combinedKey, existingLocation);

            additionalErrors.add(new ErrorRecord(
              rowIndex, "GLOBAL_UNIQUE", errorMsg, ErrorPhase.DETAIL_VALIDATION
            ));
          }
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("分布式校验 JSON 解析失败", e);
    }

    // 4. 重装配不可变的 ParseResult
    if (foundDuplicate) {
      return new ParseResult(result.jsonPayload(), ParseStatus.PARTIAL_SUCCESS, additionalErrors);
    }
    return result;
  }

  // (可选) 业务提交流程结束后，清空这个批次在 Redis 的占坑记录
  public void clearBatch(String batchId) {
    redisTemplate.delete("EXCEL_CHECK:" + batchId);
  }
}
