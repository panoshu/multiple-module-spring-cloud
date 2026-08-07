package com.pension.permission.infrastructure.permission;

import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.dto.PermissionItemDescriptor;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionScannerServiceTest {

    private PermissionItemRepository repository;
    private RequestMappingHandlerMapping handlerMapping;
    private PermissionScannerService service;

    @BeforeEach
    void setUp() {
        repository = mock(PermissionItemRepository.class);
        handlerMapping = mock(RequestMappingHandlerMapping.class);
        service = new PermissionScannerService(repository);
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
        doReturn(Optional.empty()).when(repository).findByBusinessAndAction(any(), any());
        doReturn(1).when(repository).upsertAll(anyList(), eq(UserNo.of("scanner")));

        ScanResult result = service.scanLocal(handlerMapping, UserNo.of("scanner"));

        verify(repository).upsertAll(argThat((List<PermissionItem> list) -> !list.isEmpty()), eq(UserNo.of("scanner")));
        verify(repository).markStaleForUnscanned(any(), eq(UserNo.of("scanner")));
        assertThat(result.totalReceived()).isEqualTo(1);
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(result.unchanged()).isEqualTo(0);
    }

    @Test
    void should_skip_method_without_annotation() throws Exception {
        Method method = SampleController.class.getMethod("noAnnotation");
        RequestMappingInfo info = mock(RequestMappingInfo.class);
        HandlerMethod handlerMethod = new HandlerMethod(new SampleController(), method);
        when(handlerMapping.getHandlerMethods())
            .thenReturn(Map.of(info, handlerMethod));
        doReturn(0).when(repository).upsertAll(anyList(), eq(UserNo.of("scanner")));

        ScanResult result = service.scanLocal(handlerMapping, UserNo.of("scanner"));

        verify(repository).upsertAll(argThat(List::isEmpty), eq(UserNo.of("scanner")));
        verify(repository).markStaleForUnscanned(any(), eq(UserNo.of("scanner")));
        assertThat(result.totalReceived()).isZero();
        assertThat(result.upserted()).isZero();
        assertThat(result.unchanged()).isZero();
    }

    @Test
    void should_register_from_external_without_mark_stale() {
        PermissionItemDescriptor descriptor = new PermissionItemDescriptor(
            "USER_MANAGE", "FREEZE", "PLATFORM",
            "SampleController", "freezeUser", "POST", "/api/users/freeze");
        doReturn(1).when(repository).upsertAll(anyList(), eq(UserNo.of("scanner:approval-service")));

        PermissionRegistrationResult result = service.registerFromExternal(
            "approval-service", List.of(descriptor));

        verify(repository).upsertAll(argThat((List<PermissionItem> list) -> !list.isEmpty()),
            eq(UserNo.of("scanner:approval-service")));
        verify(repository, never()).markStaleForUnscanned(any(), any());
        assertThat(result.totalReceived()).isEqualTo(1);
        assertThat(result.upserted()).isEqualTo(1);
        assertThat(result.unchanged()).isZero();
    }

    static class SampleController {
        @RequirePermission(business = "USER_MANAGE", action = "FREEZE",
            category = com.example.auth.api.annotation.PermissionCategory.PLATFORM)
        public void freezeUser() {}

        public void noAnnotation() {}
    }
}
