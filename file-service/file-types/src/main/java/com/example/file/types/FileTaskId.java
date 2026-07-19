package com.example.file.types;

import com.example.shared.primitives.identity.IdDefinition;
import com.example.shared.primitives.identity.IdType;
import com.example.shared.primitives.identity.Identifier;

import java.security.SecureRandom;
import java.util.Random;

@IdDefinition(type = IdType.ULID)
public record FileTaskId(String value) implements Identifier<String> {

  private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
  private static final int ULID_LENGTH = 26;
  private static final int TIME_PART_LENGTH = 10;
  private static final int RANDOM_PART_LENGTH = 16;

  private static final Random RANDOM = new SecureRandom();

  public FileTaskId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FileTaskId empty");
    }
  }

  public static FileTaskId of(String value) {
    return new FileTaskId(value);
  }

  public static FileTaskId generate() {
    return new FileTaskId(generateUlid());
  }

  private static String generateUlid() {
    long time = System.currentTimeMillis();
    byte[] randomBytes = new byte[10];
    RANDOM.nextBytes(randomBytes);

    char[] chars = new char[ULID_LENGTH];

    long t = time;
    for (int i = TIME_PART_LENGTH - 1; i >= 0; i--) {
      chars[i] = ENCODING[(int) (t & 0x1F)];
      t >>>= 5;
    }

    int buffer = randomBytes[0];
    int bufferBits = 8;
    int randomIndex = 1;

    for (int i = TIME_PART_LENGTH; i < ULID_LENGTH; i++) {
      if (bufferBits < 5) {
        if (randomIndex < randomBytes.length) {
          buffer = (buffer << 8) | (randomBytes[randomIndex++] & 0xFF);
          bufferBits += 8;
        } else {
          buffer = buffer << (5 - bufferBits);
          bufferBits = 5;
        }
      }
      chars[i] = ENCODING[(buffer >>> (bufferBits - 5)) & 0x1F];
      bufferBits -= 5;
    }

    return new String(chars);
  }
}
