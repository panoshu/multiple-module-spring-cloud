package com.example.bff.internet.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BffRequestDtoTest {

  @Test
  @DisplayName("BffCreateBatchRequest.toCommand 正确转换")
  void createBatchRequest_toCommand() {
    BffCreateBatchRequest request = new BffCreateBatchRequest("ACC_PLAN_CREATE", "PLAN001", "remark");

    var command = request.toCommand();

    assertEquals("ACC_PLAN_CREATE", command.businessType());
    assertEquals("PLAN001", command.planNo());
    assertEquals("remark", command.operatorRemark());
  }

  @Test
  @DisplayName("BffBatchDetailRequest.toQuery 正确剥离 businessType")
  void batchDetailRequest_toQuery() {
    BffBatchDetailRequest request = new BffBatchDetailRequest("ACC_PLAN_CREATE", "batch-123");

    var query = request.toQuery();

    assertEquals("batch-123", query.batchId());
  }

  @Test
  @DisplayName("BffBatchOverviewRequest 三个 toQuery 方法正确转换")
  void batchOverviewRequest_toQueries() {
    BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-456");

    assertEquals("batch-456", request.toBatchDetailQuery().batchId());
    assertEquals("batch-456", request.toProgressQuery().batchId());
    assertEquals("batch-456", request.toApplicationListQuery().batchId());
  }

  @Test
  @DisplayName("BffFormTokenRequest.toCommand 正确转换")
  void formTokenRequest_toCommand() {
    BffFormTokenRequest request = new BffFormTokenRequest("ACC_PLAN_CREATE", "batch-1", "data.xlsx", 1024L, "application/vnd.ms-excel");

    var command = request.toCommand();

    assertEquals("batch-1", command.batchId());
    assertEquals("data.xlsx", command.fileName());
    assertEquals(1024L, command.fileSize());
    assertEquals("application/vnd.ms-excel", command.contentType());
  }
}
