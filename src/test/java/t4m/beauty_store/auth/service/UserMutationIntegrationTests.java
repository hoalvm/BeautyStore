package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class UserMutationIntegrationTests {
    @Autowired UserRepository users;

    @Test
    void staleProfileCannotRestoreAnOldPasswordOrUndoAdministrativeBan() {
        User initial = new User();
        initial.setEmail("account-race-" + UUID.randomUUID() + "@example.test");
        initial.setPasswd("old-password-hash");
        initial.setActivated(true);
        Long id = users.saveAndFlush(initial).getId();
        try {
            User staleProfile = users.findById(id).orElseThrow();
            User latestCredentials = users.findById(id).orElseThrow();
            latestCredentials.setPasswd("new-password-hash");
            latestCredentials.setAccountLocked(true);
            latestCredentials.setActivated(false);
            latestCredentials.setAuthVersion(1);
            users.saveAndFlush(latestCredentials);

            staleProfile.setName("Concurrent profile update");
            assertThatThrownBy(() -> users.saveAndFlush(staleProfile))
                .isInstanceOf(OptimisticLockingFailureException.class);

            User preserved = users.findById(id).orElseThrow();
            assertThat(preserved.getPasswd()).isEqualTo("new-password-hash");
            assertThat(preserved.isAccountLocked()).isTrue();
            assertThat(preserved.isActivated()).isFalse();
            assertThat(preserved.getAuthVersion()).isEqualTo(1);
        } finally {
            users.deleteById(id);
        }
    }
}
