package com.example.shared.id.segment.repository;

import com.example.shared.id.segment.model.IdSegment;

/**
 * 号段仓储接口 (扩展点)
 * 实现可以是 MySQL, Redis, Zookeeper, Etcd 等
 */
public interface SegmentRepository {
  /**
   * 获取指定 sequenceKey 的下一个号段
   *
   * @param sequenceKey 物理序列键 (注意：不是业务类型，是共用的计数器Key)
   * @return 新的号段
   */
  IdSegment fetchNextSegment(String sequenceKey);
}
