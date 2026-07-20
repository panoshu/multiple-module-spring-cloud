package com.example.file.infrastructure.storage;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.FileTokenGateway;
import com.example.file.domain.model.aggregate.valueobject.FileTokenPayload;
import com.example.shared.exception.SystemException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
@RequiredArgsConstructor
public class KonaFileTokenGateway implements FileTokenGateway {

    private static final String TRANSFORMATION = "SM4/CBC/PKCS7Padding";
    private static final String PROVIDER = "KonaCrypto";
    private static final String ALGORITHM = "SM4";
    private static final int IV_LENGTH = 16;

    private final ObjectMapper objectMapper;
    private final FileTokenProperties properties;

    @Override
    public String encrypt(FileTokenPayload payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            byte[] data = json.getBytes(StandardCharsets.UTF_8);

            byte[] key = Base64.getDecoder().decode(properties.getSecretKey());
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(data);

            byte[] output = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(encrypted, 0, output, iv.length, encrypted.length);

            return Base64.getUrlEncoder().withoutPadding().encodeToString(output);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_SECRET_NOT_CONFIGURED, e)
                .withLogDetail("加密失败: " + e.getMessage());
        }
    }

    @Override
    public FileTokenPayload decrypt(String token) {
        try {
            byte[] input = Base64.getUrlDecoder().decode(token);
            byte[] iv = Arrays.copyOf(input, IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(input, IV_LENGTH, input.length);

            byte[] key = Base64.getDecoder().decode(properties.getSecretKey());
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, PROVIDER);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ALGORITHM), new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            String json = new String(decrypted, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, FileTokenPayload.class);
        } catch (Exception e) {
            throw new SystemException(FileErrorCodes.FILE_TOKEN_INVALID, e)
                .withLogDetail("解密失败: " + e.getMessage());
        }
    }
}
