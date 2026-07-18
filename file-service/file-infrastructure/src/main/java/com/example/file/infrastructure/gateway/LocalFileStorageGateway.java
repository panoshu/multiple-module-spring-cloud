package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.FileStorageGateway;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@Component
public class LocalFileStorageGateway implements FileStorageGateway {

  @Override
  public InputStream open(String fileRef) {
    try {
      return new FileInputStream(fileRef);
    } catch (FileNotFoundException e) {
      throw new IllegalArgumentException("File not found: " + fileRef, e);
    }
  }
}
