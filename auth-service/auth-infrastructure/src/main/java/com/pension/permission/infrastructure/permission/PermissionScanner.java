package com.pension.permission.infrastructure.permission;

import com.example.shared.identifier.id.UserNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 权限点自动发现扫描器.
 *
 * <p>启动时委托 {@link PermissionScannerService#scanLocal} 扫描 auth-service 本地 Controller
 * 的 {@code @RequirePermission} 注解，upsert 到 {@code t_auth_permission_item} 表。
 *
 * <p>扫描逻辑已抽取到 {@link PermissionScannerService}，本类仅负责触发时机。
 *
 * @author auth-infrastructure
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionScanner implements ApplicationRunner {

    private static final UserNo SCANNER_IDENTITY = UserNo.of("permission-scanner");

    private final PermissionScannerService scannerService;
    private final RequestMappingHandlerMapping handlerMapping;

    @Override
    public void run(ApplicationArguments args) {
        ScanResult result = scannerService.scanLocal(handlerMapping, SCANNER_IDENTITY);
        log.info("[PermissionScanner] auth-service 本地扫描完成: 发现 {}, 新增/更新 {}, 未变化 {}",
            result.totalReceived(), result.upserted(), result.unchanged());
    }
}
