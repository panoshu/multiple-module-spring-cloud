package com.example.file.domain.gateway;

import java.io.InputStream;

public interface FileStorageGateway {

  InputStream open(String fileRef);
}
