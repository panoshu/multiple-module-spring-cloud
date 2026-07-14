package com.example.file.infrastructure.excel.io;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("基础设施: ZIP防关闭流包装器测试")
class CloseShieldInputStreamTest {

  @Test
  @DisplayName("拦截 close() 测试：底层流的 close 不应该被触发")
  void testCloseIsIntercepted() throws IOException {
    // 1. Mock 一个原始流
    InputStream mockUnderlyingStream = mock(InputStream.class);

    // 2. 包装流
    CloseShieldInputStream shieldStream = new CloseShieldInputStream(mockUnderlyingStream);

    // 3. 调用包装流的 close()
    shieldStream.close();

    // 4. 验证：底层的 close() 方法绝对不能被调用 (never)
    verify(mockUnderlyingStream, never()).close();
  }

  @Test
  @DisplayName("透传 read() 测试：数据读取应当原样委托给底层流")
  void testReadIsDelegated() throws IOException {
    InputStream mockUnderlyingStream = mock(InputStream.class);

    // 模拟底层流读取一个字节，返回 ASCII 码 65 ('A')
    when(mockUnderlyingStream.read()).thenReturn(65);

    CloseShieldInputStream shieldStream = new CloseShieldInputStream(mockUnderlyingStream);
    int result = shieldStream.read();

    // 验证读取结果
    assertEquals(65, result);
    // 验证确实调用了底层流的 read
    verify(mockUnderlyingStream, times(1)).read();
  }
}
