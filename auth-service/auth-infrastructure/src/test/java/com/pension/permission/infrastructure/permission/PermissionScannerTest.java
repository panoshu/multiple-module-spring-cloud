package com.pension.permission.infrastructure.permission;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import com.pension.permission.sdk.RequirePermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionScannerTest {

  private PermissionItemRepository repository;
  private RequestMappingHandlerMapping handlerMapping;
  private PermissionScanner scanner;

  @BeforeEach
  void setUp() {
    repository = mock(PermissionItemRepository.class);
    handlerMapping = mock(RequestMappingHandlerMapping.class);
    scanner = new PermissionScanner(repository, handlerMapping);
  }

  @Test
  void should_scan_annotated_method_and_upsert() throws Exception {
    Method method = SampleController.class.getMethod("freezeUser");
    RequestMappingInfo info = mock(RequestMappingInfo.class);
    when(info.getPatternsCondition()).thenReturn(
      new org.springframework.web.servlet.mvc.condition.PatternsRequestCondition("/api/users/freeze"));
    HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);
    when(handlerMapping.getHandlerMethods())
      .thenReturn(Map.of(info, handlerMethod));
    when(repository.loadAllItems()).thenReturn(List.of());

    scanner.scan(UserNo.of("scanner"));

    verify(repository).upsertAll(anyList(), eq(UserNo.of("scanner")));
    verify(repository).markStaleForUnscanned(any(), eq(UserNo.of("scanner")));
  }

  @Test
  void should_skip_method_without_annotation() throws Exception {
    Method method = SampleController.class.getMethod("noAnnotation");
    RequestMappingInfo info = mock(RequestMappingInfo.class);
    HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);
    when(handlerMapping.getHandlerMethods())
      .thenReturn(Map.of(info, handlerMethod));
    when(repository.loadAllItems()).thenReturn(List.of());

    scanner.scan(UserNo.of("scanner"));

    verify(repository).upsertAll(argThat(List::isEmpty), eq(UserNo.of("scanner")));
    verify(repository).markStaleForUnscanned(any(), eq(UserNo.of("scanner")));
  }

  static class SampleController {
    @RequirePermission(business = "USER_MANAGE", action = "FREEZE",
      category = com.pension.permission.sdk.PermissionCategory.PLATFORM)
    public void freezeUser() {}

    public void noAnnotation() {}
  }
}
