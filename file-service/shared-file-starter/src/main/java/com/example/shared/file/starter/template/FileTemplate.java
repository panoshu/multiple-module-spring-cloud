package com.example.shared.file.starter.template;

import com.example.shared.file.api.FileApi;
import com.example.shared.file.starter.support.StreamMultipartFile;
import com.example.shared.file.types.dto.FileMetaResp;
import com.example.shared.file.types.dto.FileUrlReq;
import com.example.shared.file.types.dto.FileUrlResp;
import com.example.shared.utils.concurrent.VirtualThreadExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.util.function.Consumer;

@Slf4j
@RequiredArgsConstructor
public class FileTemplate {

  private final FileApi fileApi;

  /**
   * [流式上传] 生产者模式
   */
  public FileMetaResp upload(String fileName, String bizType, Consumer<OutputStream> streamWriter) {
    try (PipedInputStream in = new PipedInputStream();
         PipedOutputStream out = new PipedOutputStream(in)) {

      // 1. 启动虚拟线程生产数据
      VirtualThreadExecutor.executeAsync(() -> {
        try (out) {
          streamWriter.accept(out);
        } catch (Exception e) {
          log.error("Stream write failed", e);
          throw new RuntimeException("Stream write failed", e);
        }
      });

      // 2. 构造 MultipartFile 适配器
      // 注意：size 传 -1 表示长度未知，Spring Boot Client 会自动使用 Chunked 传输
      StreamMultipartFile multipartFile = new StreamMultipartFile(
        "file", fileName, "application/octet-stream", in, -1
      );

      // 3. 调用 API (修复：直接传 MultipartFile)
      // 此时 bizType 传 null 的话需要 API 允许，或者传默认值
      return fileApi.proxyUpload(multipartFile, bizType, null);

    } catch (IOException e) {
      throw new RuntimeException("Upload pipeline failed", e);
    }
  }

  /**
   * [普通上传] 便捷方法
   */
  public FileMetaResp upload(byte[] content, String fileName, String bizType) {
    return upload(fileName, bizType, out -> {
      try {
        out.write(content);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * [流式下载] 消费者模式
   */
  public void download(String fileId, Consumer<InputStream> streamConsumer) {
    // 1. 调用 API 获取 Resource
    ResponseEntity<Resource> response = fileApi.downloadStream(fileId);

    Resource resource = response.getBody();
    if (resource == null) {
      throw new RuntimeException("Download body is empty for file: " + fileId);
    }

    // 2. 消费流
    try (InputStream is = resource.getInputStream()) {
      streamConsumer.accept(is);
    } catch (IOException e) {
      throw new RuntimeException("Download stream processing failed", e);
    }
  }

  /**
   * [普通下载] 便捷方法
   */
  public byte[] download(String fileId) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    download(fileId, in -> {
      try {
        StreamUtils.copy(in, buffer);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    return buffer.toByteArray();
  }

  /**
   * 获取给前端使用的安全下载链接
   * (URL 的拼接逻辑完全由服务端控制)
   */
  public String getPresignedDownloadUrl(String fileId, String userId, String bizType, String clientIp) {
    FileUrlReq req = new FileUrlReq(fileId, userId, bizType, FileUrlReq.FileAction.DOWNLOAD, clientIp);
    FileUrlResp resp = fileApi.generatePresignedUrl(req);
    return resp.url();
  }

  /**
   * 获取给前端使用的安全上传链接
   */
  public String getPresignedUploadUrl(String userId, String bizType, String clientIp) {
    FileUrlReq req = new FileUrlReq(null, userId, bizType, FileUrlReq.FileAction.UPLOAD, clientIp);
    FileUrlResp resp = fileApi.generatePresignedUrl(req);
    return resp.url();
  }
}
