package com.example.auth.api.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionSignatureUtilsTest {

    private static final String SECRET = "test-secret-key-0123456789abcdef";

    @Test
    void sign_密钥为空_抛IllegalStateException() {
        assertThatThrownBy(() -> SessionSignatureUtils.sign("payload", ""))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("会话签名密钥未配置");
    }

    @Test
    void sign_密钥为null_抛IllegalStateException() {
        assertThatThrownBy(() -> SessionSignatureUtils.sign("payload", null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sign_生成非空签名() {
        String signature = SessionSignatureUtils.sign("payload", SECRET);
        assertThat(signature).isNotBlank().hasSize(64);
    }

    @Test
    void verify_正确密钥_返回true() {
        String payload = "U001:1234567890";
        String signature = SessionSignatureUtils.sign(payload, SECRET);
        assertThat(SessionSignatureUtils.verify(payload, signature, SECRET)).isTrue();
    }

    @Test
    void verify_错误密钥_返回false() {
        String payload = "U001:1234567890";
        String signature = SessionSignatureUtils.sign(payload, SECRET);
        assertThat(SessionSignatureUtils.verify(payload, signature, "wrong-key")).isFalse();
    }

    @Test
    void verify_篡改payload_返回false() {
        String signature = SessionSignatureUtils.sign("U001:1234567890", SECRET);
        assertThat(SessionSignatureUtils.verify("U002:1234567890", signature, SECRET)).isFalse();
    }

    @Test
    void signAccountId_返回包含loginId和expireAt的payload() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, 300L);
        assertThat(signed.payload()).startsWith("U001:");
        assertThat(signed.signature()).hasSize(64);
        assertThat(signed.expireAtEpochSecond()).isGreaterThan(System.currentTimeMillis() / 1000);
    }

    @Test
    void verifyAccountId_未过期_返回loginId() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, 300L);
        String loginId = SessionSignatureUtils.verifyAccountId(
            signed.payload(), signed.signature(), SECRET);
        assertThat(loginId).isEqualTo("U001");
    }

    @Test
    void verifyAccountId_已过期_返回null() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, -1L);
        String loginId = SessionSignatureUtils.verifyAccountId(
            signed.payload(), signed.signature(), SECRET);
        assertThat(loginId).isNull();
    }

    @Test
    void verifyAccountId_签名错误_返回null() {
        SessionSignatureUtils.SignedPayload signed =
            SessionSignatureUtils.signAccountId("U001", SECRET, 300L);
        String loginId = SessionSignatureUtils.verifyAccountId(
            signed.payload(), "wrong-signature", SECRET);
        assertThat(loginId).isNull();
    }

    @Test
    void signSessionContext_生成签名() {
        String contextBase64 = "eyJ1c2VyTm8iOiJVMDAxIn0=";
        String signature = SessionSignatureUtils.signSessionContext(
            contextBase64, System.currentTimeMillis() / 1000 + 300, SECRET);
        assertThat(signature).hasSize(64);
    }

    @Test
    void verifySessionContext_正确_返回true() {
        String contextBase64 = "eyJ1c2VyTm8iOiJVMDAxIn0=";
        long expireAt = System.currentTimeMillis() / 1000 + 300;
        String signature = SessionSignatureUtils.signSessionContext(contextBase64, expireAt, SECRET);
        assertThat(SessionSignatureUtils.verifySessionContext(contextBase64, signature, expireAt, SECRET)).isTrue();
    }

    @Test
    void verifySessionContext_过期_返回false() {
        String contextBase64 = "eyJ1c2VyTm8iOiJVMDAxIn0=";
        long expireAt = System.currentTimeMillis() / 1000 - 1;
        String signature = SessionSignatureUtils.signSessionContext(contextBase64, expireAt, SECRET);
        assertThat(SessionSignatureUtils.verifySessionContext(contextBase64, signature, expireAt, SECRET)).isFalse();
    }
}
