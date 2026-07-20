package com.example.file.api.request;

import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;

import java.time.Duration;

public record ApplyDownloadTokenRequest(
    FileId fileId,
    String sourceApp,
    CustomerNo customerNo,
    ProductNo productNo,
    UserNo downloader,
    Duration ttl
) {}
