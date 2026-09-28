package t4m.beauty_store.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import t4m.beauty_store.auth.dto.RegisterRequest;
import t4m.beauty_store.auth.dto.UpdateProfileRequest;
import t4m.beauty_store.auth.dto.UserProfileResponse;
import t4m.beauty_store.auth.entity.Role;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.RoleRepository;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.auth.util.JwtUtil;
import t4m.beauty_store.auth.exception.*;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.auth.validation.PasswordPolicy;

import lombok.RequiredArgsConstructor;
import jakarta.annotation.PostConstruct;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;
    private final StoreProperties storeProperties;
    private String dummyPasswordHash;

    @PostConstruct
    void initializeDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode("beautystore-dummy-password");
    }

    private final Cache<String, Role> roleCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .maximumSize(100)
            .build();

    /**
     * Register a new user with email and password.
     * All registrations are forced to ROLE_USER. Other roles must be created by admin.
     * Sends OTP for account activation.
     */
    public void register(RegisterRequest dto) {
        PasswordPolicy.requireStrong(dto.getPassword());
        String sanitizedEmail = dto.getEmail().trim().toLowerCase();
        // Force all public registrations to ROLE_USER only
        String sanitizedRole = "ROLE_USER";

        logger.info("Processing a customer registration");

        if (userRepository.findByEmail(sanitizedEmail).isPresent()) {
            logger.warn("Registration rejected because the email already exists");
            throw new EmailAlreadyExistsException("Email already exists");
        }

        User user = new User();
        user.setEmail(sanitizedEmail);
        user.setPasswd(passwordEncoder.encode(dto.getPassword()));
        user.setActivated(false);

        Role userRole = roleCache.get(sanitizedRole, key -> roleRepository.findByRname(key)
                .orElseThrow(() -> {
                    logger.error("Registration failed: Role not found - {}", sanitizedRole);
                    return new InvalidRoleException("Role not found: " + sanitizedRole);
                }));
        user.getRoles().add(userRole);

        userRepository.save(user);
        logger.info("Customer registered and is pending activation");

        otpService.issueOtp(sanitizedEmail, "activation");
    }

    /**
     * Send OTP for account activation.
     */
    public void sendActivationOtp(String email) {
        String sanitizedEmail = email.trim().toLowerCase();
        logger.info("Processing an activation OTP request");

        User user = userRepository.findByEmail(sanitizedEmail).orElse(null);
        if (user == null || user.isActivated()) {
            logger.info("Activation OTP request completed without a deliverable challenge");
            return;
        }

        otpService.issueOtp(sanitizedEmail, "activation");
    }

    /**
     * Verify OTP and activate account.
     */
    public void verifyOtpAndActivate(String email, String otp) {
        String sanitizedEmail = email.trim().toLowerCase();
        logger.info("Processing account activation");

        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> {
                    logger.warn("Activation rejected because the account does not exist");
                    return new UserNotFoundException("User not found");
                });

        if (user.isActivated()) {
            logger.warn("Activation rejected because the account is already active");
            throw new AccountNotActivatedException("Account already activated");
        }

        otpService.validateOtp(sanitizedEmail, otp, "activation");
        user.setActivated(true);
        userRepository.save(user);
        logger.info("Customer account activated successfully");

        // Send welcome email
        String userName = user.getName() != null ? user.getName() : sanitizedEmail;
        String ctaLink = storeProperties.absoluteUrl("/products");
        emailService.sendWelcomeEmail(sanitizedEmail, userName, ctaLink);
    }

    /**
     * Login user and generate JWT.
     */
    public String login(String email, String password) {
        String sanitizedEmail = email.trim().toLowerCase();
        logger.info("Processing a login request");

        User user = userRepository.findByEmail(sanitizedEmail).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            logger.warn("Login rejected");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!passwordEncoder.matches(password, user.getPasswd())) {
            logger.warn("Login rejected");
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!user.isActivated()) {
            logger.warn("Login rejected because the account is not active");
            throw new AccountNotActivatedException("Account not activated");
        }

        try {
            String role = user.getRoles().stream()
                    .map(Role::getRname)
                    .findFirst()
                    .orElseThrow(() -> new InvalidRoleException("No role assigned to user"));
            String token = jwtUtil.generateToken(sanitizedEmail, Set.of(role), user.getAuthVersion());
            logger.info("Login completed successfully");
            return token;
        } catch (Exception e) {
            logger.error("Could not generate a login token: {}", e.getClass().getSimpleName());
            throw new TokenGenerationException("Login failed due to token generation error");
        }
    }

    /**
     * Get user role.
     */
    public String getUserRole(String email) {
        String sanitizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> {
                    logger.warn("Role lookup rejected because the account does not exist");
                    return new UserNotFoundException("User not found");
                });
        return user.getRoles().stream()
                .map(Role::getRname)
                .findFirst()
                .orElseThrow(() -> new InvalidRoleException("No role assigned to user"));
    }

    /**
     * Send OTP for password reset.
     */
    public void sendForgotPasswordOtp(String email) {
        String sanitizedEmail = email.trim().toLowerCase();
        logger.info("Processing a password-reset OTP request");

        User user = userRepository.findByEmail(sanitizedEmail).orElse(null);
        if (user == null) {
            logger.info("Password-reset OTP request completed without a deliverable challenge");
            return;
        }

        otpService.issueOtp(sanitizedEmail, "forgot-password");
    }

    /**
     * Reset password with OTP.
     */
    public void resetPassword(String email, String otp, String newPassword) {
        PasswordPolicy.requireStrong(newPassword);
        String sanitizedEmail = email.trim().toLowerCase();
        logger.info("Processing a password reset");

        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> {
                    logger.warn("Password reset rejected");
                    return new UserNotFoundException("User not found");
                });

        otpService.validateOtp(sanitizedEmail, otp, "forgot-password");
        if (passwordEncoder.matches(newPassword, user.getPasswd())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPasswd(passwordEncoder.encode(newPassword));
        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.save(user);
        logger.info("Password reset completed successfully");
    }

    /**
     * Get user profile information.
     */
    public UserProfileResponse getUserProfile(String email) {
        String sanitizedEmail = email.trim().toLowerCase();
        logger.debug("Fetching the authenticated customer profile");

        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> {
                    logger.warn("Profile fetch rejected because the account does not exist");
                    return new UserNotFoundException("User not found");
                });

        List<String> roles = user.getRoles().stream()
                .map(Role::getRname)
                .collect(Collectors.toList());

        return new UserProfileResponse(user.getEmail(), user.getName(), user.getPhone(), user.getAddress(), roles);
    }

    /**
     * Update user profile information.
     */
    public void updateUserProfile(String email, UpdateProfileRequest dto) {
        String sanitizedEmail = email.trim().toLowerCase();
        logger.info("Processing an authenticated profile update");

        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> {
                    logger.warn("Profile update rejected because the account does not exist");
                    return new UserNotFoundException("User not found");
                });

        user.setName(dto.getName());
        user.setPhone(dto.getPhone());
        user.setAddress(dto.getAddress());
        userRepository.save(user);
        logger.info("Customer profile updated successfully");
    }

    /**
     * Change user password.
     * Validates current password before updating to new password.
     */
    public void changePassword(String email, String currentPassword, String newPassword) {
        PasswordPolicy.requireStrong(newPassword);
        String sanitizedEmail = email.trim().toLowerCase();
        logger.info("Processing an authenticated password change");

        User user = userRepository.findByEmail(sanitizedEmail)
                .orElseThrow(() -> {
                    logger.warn("Password change rejected because the account does not exist");
                    return new UserNotFoundException("User not found");
                });

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswd())) {
            logger.warn("Password change rejected because the current password is invalid");
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        if (passwordEncoder.matches(newPassword, user.getPasswd())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }

        // Update to new password
        user.setPasswd(passwordEncoder.encode(newPassword));
        user.setAuthVersion(user.getAuthVersion() + 1);
        userRepository.save(user);
        logger.info("Password changed successfully");
    }
}
