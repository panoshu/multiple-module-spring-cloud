package com.example.bff.intranet.adapter.controller;

import com.example.bff.intranet.api.BffSystemApi;
import com.example.bff.intranet.api.dto.BffBusinessTypeResponse;
import com.example.bff.intranet.api.dto.BffSystemInfoResponse;
import com.example.bff.intranet.application.service.SystemManagementService;
import com.example.shared.web.core.api.ApiResult;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统配置管理 Controller
 *
 * @author bff
 */
@RestController
public class BffSystemController implements BffSystemApi {

    private final SystemManagementService systemManagementService;

    public BffSystemController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @Override
    public ApiResult<BffSystemInfoResponse> getInfo() {
        return systemManagementService.getInfo();
    }

    @Override
    public ApiResult<List<BffBusinessTypeResponse>> listBusinessTypes() {
        return systemManagementService.listBusinessTypes();
    }
}
