package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import t4m.beauty_store.auth.exception.InvalidCredentialsException;
import t4m.beauty_store.auth.exception.AccountNotActivatedException;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.entity.Role;
import t4m.beauty_store.auth.repository.RoleRepository;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.auth.util.JwtUtil;
import t4m.beauty_store.config.StoreProperties;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserServiceSecurityTests {
    private UserRepository users;
    private PasswordEncoder passwords;
    private OtpService otp;
    private JwtUtil jwt;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwords = mock(PasswordEncoder.class);
        otp = mock(OtpService.class);
        jwt = mock(JwtUtil.class);
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        service = new UserService(users, mock(RoleRepository.class), passwords,
            jwt, otp, mock(EmailService.class), new StoreProperties());
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

    @Test
    void passwordResetConsumesAValidOtpBeforeRejectingPasswordReuse() {
        User user = new User();
        user.setEmail("customer@example.test");
        user.setPasswd("current-hash");
        when(users.findByEmail("customer@example.test")).thenReturn(Optional.of(user));
        when(passwords.matches("Secret@123", "current-hash")).thenReturn(true);

        assertThatThrownBy(() -> service.resetPassword(
                "customer@example.test", "123456", "Secret@123"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("khác");

        var order = inOrder(otp, passwords);
        order.verify(otp).validateOtp("customer@example.test", "123456", "forgot-password");
        order.verify(passwords).matches("Secret@123", "current-hash");
        verify(users, never()).save(user);
    }

    @Test
    void suspendedAccountCannotRequestActivationChallenge() {
        User user = suspendedCustomer();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.sendActivationOtp(user.getEmail());

        verifyNoInteractions(otp);
        verify(users, never()).save(any());
    }

    @Test
    void suspendedAccountCannotActivateWithAnEarlierChallenge() {
        User user = suspendedCustomer();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyOtpAndActivate(user.getEmail(), "123456"))
            .isInstanceOf(AccountNotActivatedException.class);

        verifyNoInteractions(otp);
        verify(users, never()).save(any());
        assertThat(user.isActivated()).isFalse();
        assertThat(user.isAccountNonLocked()).isFalse();
    }

    @Test
    void suspendedAccountCannotLoginEvenWithCorrectPassword() {
        User user = suspendedCustomer();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("Secret@123", user.getPasswd())).thenReturn(true);

        assertThatThrownBy(() -> service.login(user.getEmail(), "Secret@123"))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void passwordResetDoesNotClearAdministrativeSuspension() {
        User user = suspendedCustomer();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        service.resetPassword(user.getEmail(), "123456", "NewSecret@123");

        assertThat(user.isAccountLocked()).isTrue();
        assertThat(user.isActivated()).isFalse();
        assertThat(user.getAuthVersion()).isEqualTo(1);
    }

    @Test
    void pendingRegistrationCanStillRequestAndCompleteActivation() {
        User pending = new User();
        pending.setEmail("pending@example.test");
        when(users.findByEmail(pending.getEmail())).thenReturn(Optional.of(pending));

        service.sendActivationOtp(pending.getEmail());
        service.verifyOtpAndActivate(pending.getEmail(), "123456");

        verify(otp).issueOtp(pending.getEmail(), "activation");
        verify(otp).validateOtp(pending.getEmail(), "123456", "activation");
        assertThat(pending.isActivated()).isTrue();
        assertThat(pending.isAccountNonLocked()).isTrue();
    }

    @Test
    void loginAndResponseUseDeterministicRolePriorityAndImmutableIdentity() {
        User user = new User();
        user.setId(7L);
        user.setEmail("staff@example.test");
        user.setActivated(true);
        user.setPasswd("hash");
        Role customer = role("ROLE_USER");
        Role shipper = role("ROLE_SHIPPER");
        Role admin = role("ROLE_ADMIN");
        user.setRoles(Set.of(customer, shipper, admin));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwords.matches("Secret@123", "hash")).thenReturn(true);
        when(jwt.generateToken(user.getEmail(), Set.of("ROLE_ADMIN"), 0, 7L)).thenReturn("signed");

        assertThat(service.login(user.getEmail(), "Secret@123")).isEqualTo("signed");
        assertThat(service.getUserRole(user.getEmail())).isEqualTo("ROLE_ADMIN");
        user.setRoles(Set.of(customer, shipper));
        assertThat(service.getUserRole(user.getEmail())).isEqualTo("ROLE_SHIPPER");
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setRname(name);
        return role;
    }

    private static User suspendedCustomer() {
        User user = new User();
        user.setEmail("suspended@example.test");
        user.setPasswd("password-hash");
        user.setActivated(false);
        user.setAccountLocked(true);
        return user;
    }
}
