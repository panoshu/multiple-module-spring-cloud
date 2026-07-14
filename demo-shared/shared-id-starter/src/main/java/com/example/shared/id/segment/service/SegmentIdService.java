package com.example.shared.id.segment.service;

import com.example.shared.id.segment.model.IdRule;

public interface SegmentIdService {
  // 负责路由：BizType -> SequenceKey -> 拿号
  long nextRawSeq(String bizType);

  // 注册规则
  void registerRule(IdRule rule);
}
