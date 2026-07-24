package com.example.shared.id.segment.service;

import com.example.shared.id.properties.IdProperties;
import com.example.shared.id.segment.allocator.SegmentAllocator;
import com.example.shared.id.segment.model.IdRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public class DefaultSegmentIdService implements SegmentIdService {

  private final SegmentAllocator allocator;
  private final Map<String, IdRule> ruleMap = new ConcurrentHashMap<>();

  public DefaultSegmentIdService(SegmentAllocator allocator, IdProperties idProperties) {
    this.allocator = allocator;
    if (idProperties.getRules() != null) {
      idProperties.getRules().forEach((biz, key) ->
        this.registerRule(IdRule.builder().bizType(biz).sequenceKey(key).build())
      );
    }
  }

  @Override
  public void registerRule(IdRule rule) {
    ruleMap.put(rule.getBizType(), rule);
  }

  @Override
  public long nextRawSeq(String bizType) {
    // 1. 路由逻辑 (可以在这里扩展 Router 链)
    String seqKey = bizType;
    IdRule rule = ruleMap.get(bizType);
    if (rule != null && rule.getSequenceKey() != null) {
      seqKey = rule.getSequenceKey();
    }
    log.info("Segment nextRawSeq({})", seqKey);

    // 2. 调用分配器
    return allocator.nextRawId(seqKey);
  }
}
