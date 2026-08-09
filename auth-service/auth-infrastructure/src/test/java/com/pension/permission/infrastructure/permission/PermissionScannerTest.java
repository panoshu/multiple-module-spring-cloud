package com.pension.permission.infrastructure.permission;

import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PermissionScannerTest {

  private PermissionScannerService scannerService;
  private RequestMappingHandlerMapping handlerMapping;
  private PermissionScanner scanner;

  @BeforeEach
  void setUp() {
    scannerService = mock(PermissionScannerService.class);
    handlerMapping = mock(RequestMappingHandlerMapping.class);
    scanner = new PermissionScanner(scannerService, handlerMapping);
    when(scannerService.scanLocal(eq(handlerMapping), eq(UserNo.of("permission-scanner"))))
      .thenReturn(new ScanResult(1, 1, 0));
  }

  @Test
  void should_delegate_scan_to_scanner_service() {
    scanner.run(new DefaultApplicationArguments());

    verify(scannerService).scanLocal(eq(handlerMapping), eq(UserNo.of("permission-scanner")));
  }
}
