package com.example.shared.id.strategy;

import com.example.shared.id.segment.service.SegmentIdService;
import com.example.shared.primitives.identity.IdType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SegmentStrategy implements IdGenerationStrategy {

  private final SegmentIdService segmentService;

  @Override
  public IdType getSupportedType() {
    return IdType.SEGMENT;
  }

  @Override
  public boolean supportFormatting() {
    return true; // 关键：告诉门面，我只返回了纯数字，请帮我按模板格式化
  }

  @Override
  public String nextId(IdContext context) {
    return String.valueOf(this.nextLongId(context));
  }

  @Override
  public Long nextLongId(IdContext context) {
    return segmentService.nextRawSeq(context.getKey());
  }
}
