package com.pension.permission.infrastructure.channel.spi;

import com.pension.permission.domain.channel.spi.VerificationCodeHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt 实现的验证码哈希器.
 *
 * <p>使用 Spring Security 的 BCryptPasswordEncoder 对验证码进行哈希存储。
 * 明文验证码仅在应用层方法栈中短暂存在，落库前必须经过此哈希器处理。</p>
 */
@Component
public class BCryptVerificationCodeHasher implements VerificationCodeHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public String hash(String rawCode) {
        return encoder.encode(rawCode);
    }

    @Override
    public boolean matches(String rawCode, String hashedCode) {
        return encoder.matches(rawCode, hashedCode);
    }
}
