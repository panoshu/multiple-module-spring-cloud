package com.example.shared.file.starter.support;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * 自定义 MultipartFile 实现，支持流式传输，不占用内存
 */
@Getter
@Slf4j
@RequiredArgsConstructor
public class StreamMultipartFile implements MultipartFile {

  private final String name;
  private final String originalFilename;
  private final String contentType;
  private final InputStream inputStream;
  private final long size;


  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  @NonNull
  public byte[] getBytes() throws IOException {
    // 警告：这会把流读入内存，破坏流式初衷，但在某些 fallback 场景可能被调用
    return FileCopyUtils.copyToByteArray(inputStream);
  }

  @Override
  public void transferTo(@NonNull File dest) throws IOException, IllegalStateException {
    try (
      InputStream in = inputStream;
      var out = Files.newOutputStream(dest.toPath())
    ) {
      FileCopyUtils.copy(in, out);
    }
  }
}
