package com.example.integration.infrastructure.core.common.adapter;

import com.example.shared.client.decoder.SimpleErrorDecoder;
import com.example.file.api.FileAccessApi;
import com.github.lianjiatech.retrofit.spring.boot.core.RetrofitClient;
import retrofit2.http.Body;
import retrofit2.http.POST;

/**
 * file-service Retrofit 客户端
 * <p>
 * 用于从 file-service 下载出站文件并转发至外部交易系统。
 * 仅封装 token 申请与文件下载两个操作（使用 FileAccessApi 协议）。
 */
@RetrofitClient(
  baseUrl = "${external.file-service.url:http://localhost:8083/}",
  errorDecoder = SimpleErrorDecoder.class
)
public interface FileServiceClient {

  /**
   * 申请文件下载令牌
   */
  @POST("api/file/access/download-tokens")
  Object applyDownloadToken(@Body Object request);

  /**
   * 通过令牌下载文件（返回原始响应体）
   */
  @POST("api/file/access/download")
  Object download(@Body Object request);
}
