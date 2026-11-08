package com.example.bff.shared.business.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BffRequestDtoTest {

  @Test
  @DisplayName("BffCreateBatchRequest 记录访问器正确")
  void createBatchRequest_accessors() {
    BffCreateBatchRequest request = new BffCreateBatchRequest("ACC_PLAN_CREATE", "PLAN001", "remark");

    assertEquals("ACC_PLAN_CREATE", request.businessType());
    assertEquals("PLAN001", request.planNo());
    assertEquals("remark", request.operatorRemark());
  }

  @Test
  @DisplayName("BffBatchDetailRequest 记录访问器正确")
  void batchDetailRequest_accessors() {
    BffBatchDetailRequest request = new BffBatchDetailRequest("ACC_PLAN_CREATE", "batch-123");

    assertEquals("ACC_PLAN_CREATE", request.businessType());
    assertEquals("batch-123", request.batchId());
  }

  @Test
  @DisplayName("BffBatchOverviewRequest 记录访问器正确")
  void batchOverviewRequest_accessors() {
    BffBatchOverviewRequest request = new BffBatchOverviewRequest("ACC_PLAN_CREATE", "batch-456");

    assertEquals("ACC_PLAN_CREATE", request.businessType());
    assertEquals("batch-456", request.batchId());
  }

  @Test
  @DisplayName("BffFormTokenRequest 记录访问器正确")
  void formTokenRequest_accessors() {
    BffFormTokenRequest request = new BffFormTokenRequest("ACC_PLAN_CREATE", "batch-1", "data.xlsx", 1024L, "application/vnd.ms-excel");

    assertEquals("ACC_PLAN_CREATE", request.businessType());
    assertEquals("batch-1", request.batchId());
    assertEquals("data.xlsx", request.fileName());
    assertEquals(1024L, request.fileSize());
    assertEquals("application/vnd.ms-excel", request.contentType());
  }

  @Test
  @DisplayName("BffSubmitRequest 记录访问器正确")
  void submitRequest_accessors() {
    BffSubmitRequest request = new BffSubmitRequest("ACC_PLAN_CREATE", "app-1");

    assertEquals("ACC_PLAN_CREATE", request.businessType());
    assertEquals("app-1", request.applicationId());
  }

  @Test
  @DisplayName("BffApplicationDetailRequest 记录访问器正确")
  void applicationDetailRequest_accessors() {
    BffApplicationDetailRequest request = new BffApplicationDetailRequest("ACC_PLAN_CREATE", "app-2");

    assertEquals("ACC_PLAN_CREATE", request.businessType());
    assertEquals("app-2", request.applicationId());
  }

  @Test
  @DisplayName("BffListMaterialsRequest 记录访问器正确")
  void listMaterialsRequest_accessors() {
    BffListMaterialsRequest request = new BffListMaterialsRequest("ACC_PLAN_CREATE", "app-3");

    assertEquals("ACC_PLAN_CREATE", request.businessType());
    assertEquals("app-3", request.applicationId());
  }
}
