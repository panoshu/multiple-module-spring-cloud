package com.example.shared.id.segment.allocator;

public interface SegmentAllocator {
  long nextRawId(String sequenceKey);
}
