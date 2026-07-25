package com.example.iam.domain.authentication.strategy;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.CredentialId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CredentialSecondaryAuthStrategyTest {

    @Test
    void authenticate_should_return_true_when_password_matches() {
        String plain = "HrPwd123";
        String hash = BCrypt.hashpw(plain, BCrypt.gensalt());
        Credential credential = Credential.reconstitute(
            CredentialId.of(1L), "INTERNET_USER", 100L, CredentialType.PASSWORD,
            hash, null, UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U001"), UserNo.of("U001"),
            LocalDateTime.now(), LocalDateTime.now(), Version.initial()
        );
        SecondaryAuthSession session = createPendingSession();
        CredentialSecondaryAuthStrategy strategy = new CredentialSecondaryAuthStrategy();
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = strategy.authenticate(session, plain, List.of(credential), validator);

        assertTrue(result);
    }

    @Test
    void authenticate_should_return_false_when_password_not_matches() {
        String hash = BCrypt.hashpw("CorrectPwd", BCrypt.gensalt());
        Credential credential = Credential.reconstitute(
            CredentialId.of(1L), "INTERNET_USER", 100L, CredentialType.PASSWORD,
            hash, null, UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U001"), UserNo.of("U001"),
            LocalDateTime.now(), LocalDateTime.now(), Version.initial()
        );
        SecondaryAuthSession session = createPendingSession();
        CredentialSecondaryAuthStrategy strategy = new CredentialSecondaryAuthStrategy();
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = strategy.authenticate(session, "WrongPwd", List.of(credential), validator);

        assertFalse(result);
    }

    @Test
    void authenticate_should_return_false_when_no_password_credential() {
        // 仅有 UKEY 凭据，无 PASSWORD 凭据
        Credential ukeyCredential = Credential.reconstitute(
            CredentialId.of(1L), "INTERNET_USER", 100L, CredentialType.UKEY,
            "ukey-data", "salt", UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U001"), UserNo.of("U001"),
            LocalDateTime.now(), LocalDateTime.now(), Version.initial()
        );
        SecondaryAuthSession session = createPendingSession();
        CredentialSecondaryAuthStrategy strategy = new CredentialSecondaryAuthStrategy();
        PasswordCredentialValidator validator = new PasswordCredentialValidator();

        boolean result = strategy.authenticate(session, "anyInput", List.of(ukeyCredential), validator);

        assertFalse(result);
    }

    @Test
    void supportedType_should_return_credential() {
        assertEquals(SecondaryAuthStrategyType.CREDENTIAL,
            new CredentialSecondaryAuthStrategy().supportedType());
    }

    private SecondaryAuthSession createPendingSession() {
        return SecondaryAuthSession.reconstitute(
            SecondaryAuthSessionId.of(1L),
            BranchUserId.of(2L), InternetUserId.of(100L),
            SecondaryAuthStrategyType.CREDENTIAL,
            LocalDateTime.now().plusMinutes(30),
            SecondaryAuthStatus.PENDING, null,
            UserNo.of("U002"), UserNo.of("U002"),
            LocalDateTime.now(), LocalDateTime.now(), Version.initial()
        );
    }
}
