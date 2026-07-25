package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

class PasswordCredentialValidatorTest {

    @Test
    void verify_should_return_true_when_password_matches() {
        String plain = "myPassword123";
        String hash = BCrypt.hashpw(plain, BCrypt.gensalt());
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = validator.verify(plain, hash, null, CredentialType.PASSWORD);

        assertTrue(result);
    }

    @Test
    void verify_should_return_false_when_password_not_matches() {
        String hash = BCrypt.hashpw("correctPassword", BCrypt.gensalt());
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = validator.verify("wrongPassword", hash, null, CredentialType.PASSWORD);

        assertFalse(result);
    }

    @Test
    void supportedType_should_return_password() {
        assertEquals(CredentialType.PASSWORD, new PasswordCredentialValidator().supportedType());
    }

    @Test
    void hashPassword_should_return_bcrypt_hash() {
        String hash = PasswordCredentialValidator.hashPassword("myPassword");

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$"));
        assertTrue(BCrypt.checkpw("myPassword", hash));
    }
}
