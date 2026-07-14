package com.example.file.infrastructure.excel.io;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * JsonStreamWriter
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 22:53
 */
public class JsonStreamWriter implements AutoCloseable {
  private final OutputStream out;
  private final JsonGenerator generator;
  private final ObjectMapper mapper = new ObjectMapper();

  public JsonStreamWriter(OutputStream out) {
    this.out = out;
    try {
      this.generator = new JsonFactory().createGenerator(out, JsonEncoding.UTF8);
      this.generator.writeStartArray();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void write(Map<String, Object> data) {
    try {
      mapper.writeValue(generator, data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      if (generator != null && !generator.isClosed()) {
        generator.writeEndArray();
        generator.close();
      }
      if (out != null) {
        out.close(); // Triggers OSS Complete Multipart Upload
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
