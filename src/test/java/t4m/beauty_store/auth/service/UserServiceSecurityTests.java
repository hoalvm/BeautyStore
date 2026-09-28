package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import t4m.beauty_store.auth.exception.InvalidCredentialsException;
import t4m.beauty_store.auth.repository.RoleRepository;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.auth.util.JwtUtil;
import t4m.beauty_store.config.StoreProperties;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserServiceSecurityTests {
    private UserRepository users;
    private PasswordEncoder passwords;
    private OtpService otp;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = mock(PasswordEncoder.class);
        otp = mock(OtpService.class);
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        service = new UserService(users, mock(RoleRepository.class), passwords,
            mock(JwtUtil.class), otp, mock(EmailService.class), new StoreProperties());
        service.initializeDummyPasswordHash();
    }

    @Test
    void missingAccountLoginStillPerformsPasswordWork() {
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("missing@example.test", "Secret@123"))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid email or password");

        verify(passwords).matches("Secret@123", "dummy-hash");
    }

    @Test
    void otpRequestsDoNotRevealMissingAccount() {
        when(users.findByEmail("missing@example.test")).thenReturn(Optional.empty());

        service.sendForgotPasswordOtp("missing@example.test");
        service.sendActivationOtp("missing@example.test");

        verifyNoInteractions(otp);
    }
}
