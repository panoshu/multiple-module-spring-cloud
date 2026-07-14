package com.example.file.infrastructure.excel.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * CloseShieldInputStream
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 22:53
 */
public class CloseShieldInputStream extends InputStream {
  private final InputStream in;

  public CloseShieldInputStream(InputStream in) {
    this.in = in;
  }

  @Override
  public int read() throws IOException {
    return in.read();
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    return in.read(b, off, len);
  }

  @Override
  public void close() { /* Intercepted to protect underlying stream */ }
}
