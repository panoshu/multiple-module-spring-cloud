package com.example.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * FileApplication
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/25 20:34
 */

@SpringBootApplication
@ConfigurationPropertiesScan
public class FileApplication {

  public static void main(String[] args) {
    SpringApplication.run(FileApplication.class, args);
  }
}
