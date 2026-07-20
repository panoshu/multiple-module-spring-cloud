package com.example.file.api;

import com.example.file.api.request.ApplyDownloadTokenRequest;
import com.example.file.api.request.ApplyUploadTokenRequest;
import com.example.file.api.response.ApplyDownloadTokenResponse;
import com.example.file.api.response.ApplyUploadTokenResponse;
import com.example.file.api.response.UploadFileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@HttpExchange(url = "/api/file/access")
public interface FileAccessApi {

    @PostExchange(url = "/upload-tokens")
    ApplyUploadTokenResponse applyUploadToken(@RequestBody ApplyUploadTokenRequest request);

    @PostExchange(url = "/download-tokens")
    ApplyDownloadTokenResponse applyDownloadToken(@RequestBody ApplyDownloadTokenRequest request);

    @PostExchange(url = "/upload")
    UploadFileResponse upload(
        @RequestHeader("X-File-Token") String token,
        @RequestPart("file") MultipartFile file
    );

    @GetExchange(url = "/download")
    ResponseEntity<StreamingResponseBody> download(@RequestHeader("X-File-Token") String token);
}
