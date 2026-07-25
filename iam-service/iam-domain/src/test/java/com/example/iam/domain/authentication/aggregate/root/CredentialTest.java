package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.CredentialId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialTest {

    @Test
    void create_should_return_active_credential() {
        UserNo owner = UserNo.of("U001");
        Credential credential = Credential.create(
            CredentialId.of(1L), CredentialType.PASSWORD,
            "hashed-secret", "salt-value", owner
        );

        assertEquals(CredentialId.of(1L), credential.id());
        assertEquals(CredentialType.PASSWORD, credential.credentialType());
        assertEquals("hashed-secret", credential.secret());
        assertEquals(UserStatus.ACTIVE, credential.status());
    }

    @Test
    void changeSecret_should_update_secret_and_increment_version() {
        Credential credential = createActiveCredential();
        long oldVersion = credential.version().value();

        credential.changeSecret("new-secret", "new-salt", UserNo.of("U002"));

        assertEquals("new-secret", credential.secret());
        assertEquals("new-salt", credential.salt());
        assertTrue(credential.version().value() > oldVersion);
    }

    @Test
    void disable_should_mark_credential_disabled() {
        Credential credential = createActiveCredential();
        credential.disable(UserNo.of("U002"));
        assertEquals(UserStatus.DISABLED, credential.status());
    }

    private Credential createActiveCredential() {
        return Credential.create(CredentialId.of(1L), CredentialType.PASSWORD,
            "hashed-secret", "salt-value", UserNo.of("U001"));
    }
}
